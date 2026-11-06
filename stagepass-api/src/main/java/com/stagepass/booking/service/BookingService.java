package com.stagepass.booking.service;

import com.stagepass.booking.api.BookingResponse;
import com.stagepass.booking.api.CreateBookingRequest;
import com.stagepass.booking.api.QuoteRequest;
import com.stagepass.booking.api.QuoteResponse;
import com.stagepass.booking.domain.Booking;
import com.stagepass.booking.domain.BookingItem;
import com.stagepass.booking.domain.BookingStatus;
import com.stagepass.booking.repository.BookingRepository;
import com.stagepass.common.config.StagepassProperties;
import com.stagepass.common.error.ApiException;
import com.stagepass.common.error.ErrorCode;
import com.stagepass.common.error.FieldErrors;
import com.stagepass.payment.domain.Payment;
import com.stagepass.payment.repository.PaymentRepository;
import com.stagepass.payment.service.RazorpayClient;
import com.stagepass.payment.service.RazorpayModels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Quotes and creates bookings. Creation runs in three steps so no database transaction waits on Razorpay:
 * (1) hold tickets and write the booking, (2) create the Razorpay order, (3) record the payment row.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    /** The booking plus whether this call created it (201) or replayed an earlier call with the same key (200). */
    public record Result(BookingResponse booking, boolean created) {
    }

    private final BookingRules rules;
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final InventoryService inventory;
    private final BookingExpiryService expiry;
    private final BookingConfirmationService confirmation;
    private final BookingViews views;
    private final RazorpayClient razorpay;
    private final StagepassProperties props;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;

    public BookingService(BookingRules rules, BookingRepository bookings, PaymentRepository payments,
            InventoryService inventory, BookingExpiryService expiry, BookingConfirmationService confirmation,
            BookingViews views, RazorpayClient razorpay, StagepassProperties props, Clock clock,
            PlatformTransactionManager txManager) {
        this.rules = rules;
        this.bookings = bookings;
        this.payments = payments;
        this.inventory = inventory;
        this.expiry = expiry;
        this.confirmation = confirmation;
        this.views = views;
        this.razorpay = razorpay;
        this.props = props;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
    }

    public QuoteResponse quote(QuoteRequest r) {
        return readTx.execute(s -> {
            BookingRules.Checked c = rules.check(r.eventId(), r.items(), null);
            return new QuoteResponse(c.quoteLines(), c.price(), props.holdMinutes());
        });
    }

    public Result create(UUID userId, String idempotencyKey, CreateBookingRequest r) {
        if (idempotencyKey == null || idempotencyKey.length() < 8 || idempotencyKey.length() > 100) {
            throw FieldErrors.single("Idempotency-Key", "must be 8-100 characters");
        }
        Optional<Result> replay = replay(userId, idempotencyKey, r);
        if (replay.isPresent()) {
            return replay.get();
        }

        UUID bookingId;
        try {
            bookingId = tx.execute(s -> reserve(userId, idempotencyKey, r));
        } catch (DataIntegrityViolationException e) {
            // Same key sent twice at once, or two checkouts for one event at once: answer like the winner did.
            return replay(userId, idempotencyKey, r).orElseThrow(
                    () -> ApiException.conflict("You already have a checkout in progress for this event"));
        }

        Booking pending = readTx.execute(s -> bookings.findById(bookingId).orElseThrow());
        if (pending.getStatus() == BookingStatus.CONFIRMED) {
            return new Result(view(bookingId), true);
        }

        RazorpayModels.Order order;
        try {
            order = razorpay.createOrder(pending.getTotalPaise(), pending.getCode(),
                    Map.of("bookingId", bookingId.toString(), "bookingCode", pending.getCode()));
        } catch (RuntimeException e) {
            log.warn("Razorpay order creation failed for booking {}: {}", pending.getCode(), e.getMessage());
            tx.executeWithoutResult(s -> expiry.expire(bookingId, "PAYMENT_GATEWAY_UNAVAILABLE"));
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Payments are temporarily unavailable. Your tickets were released - please try again shortly.");
        }

        tx.executeWithoutResult(s -> {
            Payment p = new Payment();
            p.setBookingId(bookingId);
            p.setRazorpayOrderId(order.id());
            p.setAmountPaise(pending.getTotalPaise());
            payments.save(p);
        });
        return new Result(view(bookingId), true);
    }

    public BookingResponse view(UUID bookingId) {
        return readTx.execute(s -> views.view(bookings.findById(bookingId).orElseThrow(() -> ApiException.notFound("Booking"))));
    }

    /** Step 1: replace any earlier pending checkout, validate, hold tickets, write the booking. */
    private UUID reserve(UUID userId, String key, CreateBookingRequest r) {
        bookings.findFirstByUserIdAndEventIdAndStatus(userId, r.eventId(), BookingStatus.PENDING_PAYMENT)
                .ifPresent(previous -> expiry.expire(previous.getId(), "REPLACED"));

        BookingRules.Checked c = rules.check(r.eventId(), r.items(), userId);
        for (BookingRules.Line line : c.lines()) {
            if (!inventory.hold(line.type().getId(), line.quantity())) {
                throw new ApiException(ErrorCode.SOLD_OUT, "Not enough " + line.type().getName() + " tickets left");
            }
        }

        Booking b = new Booking();
        b.setCode(uniqueCode());
        b.setUserId(userId);
        b.setEventId(r.eventId());
        b.setSubtotalPaise(c.price().subtotalPaise());
        b.setFeePaise(c.price().feePaise());
        b.setFeeGstPaise(c.price().feeGstPaise());
        b.setTotalPaise(c.price().totalPaise());
        b.setCommissionPaise(c.price().commissionPaise());
        b.setHoldExpiresAt(OffsetDateTime.now(clock).plusMinutes(props.holdMinutes()));
        b.setAttendeeName(r.attendeeName().strip());
        b.setAttendeeEmail(r.attendeeEmail().strip().toLowerCase());
        b.setAttendeePhone(r.attendeePhone());
        b.setBuyerState(r.buyerState() == null || r.buyerState().isBlank() ? null : r.buyerState().strip());
        b.setIdempotencyKey(key);
        for (BookingRules.Line line : c.lines()) {
            BookingItem item = new BookingItem();
            item.setBooking(b);
            item.setTicketTypeId(line.type().getId());
            item.setQuantity(line.quantity());
            item.setUnitPricePaise(line.type().getPricePaise());
            b.getItems().add(item);
        }
        bookings.saveAndFlush(b);
        if (b.getTotalPaise() == 0) {
            confirmation.confirmFree(b);
        }
        return b.getId();
    }

    /** An earlier booking made with this key: the same one again, or 409 if the key was used for something else. */
    private Optional<Result> replay(UUID userId, String key, CreateBookingRequest r) {
        return readTx.execute(s -> bookings.findByIdempotencyKey(key).map(b -> {
            if (!b.getUserId().equals(userId) || !samePayload(b, r)) {
                throw ApiException.conflict("This Idempotency-Key was already used for a different booking");
            }
            return new Result(views.view(b), false);
        }));
    }

    private static boolean samePayload(Booking b, CreateBookingRequest r) {
        if (!b.getEventId().equals(r.eventId())) {
            return false;
        }
        Map<UUID, Integer> requested = new HashMap<>();
        r.items().forEach(i -> requested.merge(i.ticketTypeId(), i.quantity(), Integer::sum));
        Map<UUID, Integer> booked = new HashMap<>();
        b.getItems().forEach(i -> booked.put(i.getTicketTypeId(), i.getQuantity()));
        return requested.equals(booked);
    }

    private String uniqueCode() {
        while (true) {
            String code = BookingCodes.booking();
            if (!bookings.existsByCode(code)) {
                return code;
            }
        }
    }
}

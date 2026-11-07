package com.stagepass.booking.service;

import com.stagepass.booking.api.BookingResponse;
import com.stagepass.booking.api.OrganizerRefundView;
import com.stagepass.booking.api.RefundRequest;
import com.stagepass.booking.domain.Booking;
import com.stagepass.booking.domain.BookingItem;
import com.stagepass.booking.domain.BookingStatus;
import com.stagepass.booking.domain.PriceBreakdown;
import com.stagepass.booking.domain.Refund;
import com.stagepass.booking.domain.RefundKind;
import com.stagepass.booking.domain.RefundStatus;
import com.stagepass.booking.domain.Ticket;
import com.stagepass.booking.domain.TicketStatus;
import com.stagepass.booking.repository.BookingRepository;
import com.stagepass.booking.repository.RefundRepository;
import com.stagepass.catalog.domain.Event;
import com.stagepass.catalog.domain.EventStatus;
import com.stagepass.catalog.repository.EventRepository;
import com.stagepass.common.audit.AuditLogService;
import com.stagepass.common.error.ApiException;
import com.stagepass.common.error.ErrorCode;
import com.stagepass.common.error.FieldErrors;
import com.stagepass.payment.domain.PaymentStatus;
import com.stagepass.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Per-ticket refunds (spec 8.4, 8.4a). Applying a refund happens in one transaction (tickets REFUNDED, inventory
 * back on sale, ledger reversed, refund row REQUESTED); Razorpay is called after commit. If Razorpay rejects it,
 * everything is put back ({@link #restore}).
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final BookingRepository bookings;
    private final RefundRepository refunds;
    private final PaymentRepository payments;
    private final EventRepository events;
    private final InventoryService inventory;
    private final LedgerService ledger;
    private final RefundGateway gateway;
    private final RefundEligibility eligibility;
    private final FeeSettingsRepository fees;
    private final BookingExpiryService expiry;
    private final BookingService bookingService;
    private final AuditLogService audit;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final BookingEmails emails;

    public RefundService(BookingRepository bookings, RefundRepository refunds, PaymentRepository payments,
            EventRepository events, InventoryService inventory, LedgerService ledger, RefundGateway gateway,
            RefundEligibility eligibility, FeeSettingsRepository fees, BookingExpiryService expiry,
            BookingService bookingService, AuditLogService audit, Clock clock, PlatformTransactionManager txManager,
            BookingEmails emails) {
        this.emails = emails;
        this.bookings = bookings;
        this.refunds = refunds;
        this.payments = payments;
        this.events = events;
        this.inventory = inventory;
        this.ledger = ledger;
        this.gateway = gateway;
        this.eligibility = eligibility;
        this.fees = fees;
        this.expiry = expiry;
        this.bookingService = bookingService;
        this.audit = audit;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
        // Also runs from the event-cancellation after-commit callback, where a joined transaction would never commit.
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ---------------------------------------------------------------- attendee

    /** Attendee asks for a refund: immediate when the policy allows it, otherwise queued for the organizer. */
    public BookingResponse request(UUID userId, UUID bookingId, RefundRequest r) {
        UUID toSubmit = tx.execute(s -> {
            bookings.findByIdAndUserId(bookingId, userId).orElseThrow(() -> ApiException.notFound("Booking"));
            Booking b = lockRefundable(bookingId);
            Event e = events.findById(b.getEventId()).orElseThrow();
            RefundEligibility.Eligibility el = eligibility.forEvent(e, OffsetDateTime.now(clock));
            if (!el.requestable()) {
                throw new ApiException(ErrorCode.INVALID_STATE, el.reason());
            }
            List<Ticket> chosen = pick(b, r.ticketIdsOrEmpty());
            String reason = r.reason() == null || r.reason().isBlank() ? null : r.reason().strip();
            if (el.selfServe()) {
                RefundKind kind = RefundEligibility.RESCHEDULE.equals(el.kind()) ? RefundKind.RESCHEDULE
                        : RefundKind.ATTENDEE_REQUEST;
                return apply(b, chosen, kind, el.includesFee(), userId, null, reason, null).getId();
            }
            Refund pending = row(b, chosen, RefundKind.ATTENDEE_REQUEST,
                    fees.forEvent(b.getEventId()).refundFeeOnAttendeeRequest(), userId, reason, null);
            pending.setStatus(RefundStatus.PENDING_APPROVAL);
            refunds.save(pending);
            audit.record(userId, "REFUND_REQUESTED", "booking", b.getId().toString(), null,
                    Map.of("refundId", pending.getId().toString(), "tickets", chosen.size(), "needsApproval", true));
            return null;
        });
        if (toSubmit != null) {
            submitOrFail(toSubmit);
        }
        return bookingService.view(bookingId);
    }

    // ---------------------------------------------------------------- organizer

    public List<OrganizerRefundView> listForEvent(UUID organizerId, UUID eventId) {
        return tx.execute(s -> {
            ownedEvent(organizerId, eventId);
            List<Refund> rows = refunds.findByEventId(eventId);
            Map<UUID, Booking> byId = bookings.findAllById(rows.stream().map(Refund::getBookingId).distinct().toList())
                    .stream().collect(Collectors.toMap(Booking::getId, b -> b));
            return rows.stream().map(r -> {
                Booking b = byId.get(r.getBookingId());
                return new OrganizerRefundView(r.getId(), b.getId(), b.getCode(), b.getAttendeeName(),
                        b.getAttendeeEmail(), r.getKind().name(), r.getStatus().name(), r.getTicketIds().length,
                        r.getAmountPaise(), r.getReason(),
                        r.getStatus() == RefundStatus.DECLINED ? r.getFailureReason() : null, r.getCreatedAt());
            }).toList();
        });
    }

    public OrganizerRefundView approve(UUID organizerId, UUID actorId, UUID refundId) {
        UUID eventId = tx.execute(s -> {
            Refund r = pendingForOrganizer(organizerId, refundId);
            Booking b = lockRefundable(r.getBookingId());
            Set<UUID> ids = Set.of(r.getTicketIds());
            List<Ticket> tickets = b.getTickets().stream().filter(t -> ids.contains(t.getId())).toList();
            if (tickets.size() != ids.size() || tickets.stream().anyMatch(t -> t.getStatus() != TicketStatus.VALID)) {
                throw new ApiException(ErrorCode.INVALID_STATE, "Some of these tickets can no longer be refunded.");
            }
            boolean includesFee = r.getFeePaise() > 0 || r.getFeeGstPaise() > 0;
            apply(b, tickets, r.getKind(), includesFee, r.getRequestedBy(), actorId, r.getReason(), r);
            return b.getEventId();
        });
        submitOrFail(refundId);
        return view(organizerId, eventId, refundId);
    }

    public OrganizerRefundView decline(UUID organizerId, UUID actorId, UUID refundId, String note) {
        UUID eventId = tx.execute(s -> {
            Refund r = pendingForOrganizer(organizerId, refundId);
            r.setStatus(RefundStatus.DECLINED);
            r.setDecidedBy(actorId);
            r.setFailureReason(note == null || note.isBlank() ? null : note.strip());
            refunds.save(r);
            audit.record(actorId, "REFUND_DECLINED", "refund", r.getId().toString(), null,
                    note == null ? Map.of() : Map.of("note", note));
            emails.refundDeclined(r, note);
            return bookings.findById(r.getBookingId()).orElseThrow().getEventId();
        });
        return view(organizerId, eventId, refundId);
    }

    // ---------------------------------------------------------------- admin

    /**
     * Admin refunds the chosen tickets (or every live, unclaimed one) whatever the event's policy, returning the
     * convenience fee when {@code includeFee}. Same apply/submit path as an organizer approval.
     */
    public BookingResponse adminRefund(UUID adminId, UUID bookingId, List<UUID> ticketIds, boolean includeFee,
            String reason) {
        UUID refundId = tx.execute(s -> {
            Booking b = lockRefundable(bookingId);
            List<Ticket> chosen = pick(b, ticketIds == null ? List.of() : ticketIds);
            Refund r = apply(b, chosen, RefundKind.ADMIN, includeFee, null, adminId, reason, null);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("refundId", r.getId().toString());
            details.put("byAdmin", true);
            details.put("includeFee", includeFee);
            details.put("tickets", chosen.size());
            details.put("reason", reason);
            audit.record(adminId, "REFUND_ISSUED", "booking", b.getId().toString(), null, details);
            return r.getId();
        });
        submitOrFail(refundId);
        return bookingService.view(bookingId);
    }

    // ---------------------------------------------------------------- event cancellation

    /**
     * Refunds every ticket of a cancelled event in full (fees included) and ends open checkouts. Idempotent and
     * safe to repeat: each booking is handled on its own, so one failure doesn't stop the rest.
     */
    public int refundEventCancellation(UUID eventId, UUID actorId) {
        List<UUID> pending = tx.execute(s -> bookings.findByEventIdAndStatusIn(eventId,
                List.of(BookingStatus.PENDING_PAYMENT)).stream().map(Booking::getId).toList());
        for (UUID id : pending) {
            try {
                tx.executeWithoutResult(s -> expiry.expire(id, "EVENT_CANCELLED"));
            } catch (RuntimeException e) {
                log.error("Could not end checkout {} of cancelled event {}; will retry", id, eventId, e);
            }
        }

        tx.executeWithoutResult(s -> refunds.findByEventId(eventId).stream()
                .filter(r -> r.getStatus() == RefundStatus.PENDING_APPROVAL)
                .forEach(r -> {
                    r.setStatus(RefundStatus.DECLINED);
                    r.setDecidedBy(actorId);
                    r.setFailureReason("Replaced by a full refund because the event was cancelled");
                    refunds.save(r);
                }));

        List<UUID> confirmed = tx.execute(s -> bookings.findByEventIdAndStatusIn(eventId,
                List.of(BookingStatus.CONFIRMED, BookingStatus.PARTIALLY_REFUNDED)).stream().map(Booking::getId).toList());
        int issued = 0;
        for (UUID bookingId : confirmed) {
            try {
                UUID refundId = tx.execute(s -> {
                    Booking b = bookings.findForUpdate(bookingId).orElseThrow();
                    Set<UUID> claimed = RefundEligibility.claimedTicketIds(refunds.findByBookingIdOrderByCreatedAtDesc(bookingId));
                    List<Ticket> live = b.getTickets().stream()
                            .filter(t -> t.getStatus() == TicketStatus.VALID && !claimed.contains(t.getId())).toList();
                    if (live.isEmpty()) {
                        return null;
                    }
                    Refund refund = apply(b, live, RefundKind.CANCELLATION, true, null, actorId, "Event cancelled", null);
                    emails.eventCancelled(b, refund.getAmountPaise());
                    return refund.getId();
                });
                if (refundId != null) {
                    issued++;
                    submit(refundId);
                }
            } catch (RuntimeException e) {
                log.error("Cancellation refund for booking {} failed; the sweep job will retry", bookingId, e);
            }
        }
        return issued;
    }

    // ---------------------------------------------------------------- webhooks and retries

    public void onProcessed(String razorpayRefundId, String ourRefundId) {
        gateway.onProcessed(razorpayRefundId, ourRefundId);
    }

    public void onFailed(String razorpayRefundId, String ourRefundId, String reason) {
        gateway.onFailed(razorpayRefundId, ourRefundId, reason).ifPresent(this::afterFailure);
    }

    /** Sends a REQUESTED refund to Razorpay; a definite refusal is handled by {@link #afterFailure}. Call outside a transaction. */
    public RefundStatus submit(UUID refundId) {
        RefundStatus status = gateway.submit(refundId);
        if (status == RefundStatus.FAILED) {
            afterFailure(refundId);
        }
        return status;
    }

    /** Sends a failed refund that is queued for retry again (status back to REQUESTED). */
    public RefundStatus retry(UUID refundId) {
        Boolean ready = tx.execute(s -> refunds.findById(refundId)
                .filter(r -> r.getStatus() == RefundStatus.FAILED && r.isNeedsRetry())
                .map(r -> {
                    r.setStatus(RefundStatus.REQUESTED);
                    refunds.save(r);
                    return true;
                }).orElse(false));
        return Boolean.TRUE.equals(ready) ? submit(refundId) : RefundStatus.FAILED;
    }

    // ---------------------------------------------------------------- internals

    private void submitOrFail(UUID refundId) {
        if (submit(refundId) == RefundStatus.FAILED) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                    "The refund could not be started with the payment gateway. Your tickets are unchanged - please try again.");
        }
    }

    private Booking lockRefundable(UUID bookingId) {
        Booking b = bookings.findForUpdate(bookingId).orElseThrow(() -> ApiException.notFound("Booking"));
        if (b.getStatus() != BookingStatus.CONFIRMED && b.getStatus() != BookingStatus.PARTIALLY_REFUNDED) {
            throw new ApiException(ErrorCode.INVALID_STATE, "This booking has no tickets that can be refunded.");
        }
        return b;
    }

    /** The requested tickets (or every refundable one), each VALID and not already claimed by another refund. */
    private List<Ticket> pick(Booking b, List<UUID> ticketIds) {
        Set<UUID> claimed = RefundEligibility.claimedTicketIds(refunds.findByBookingIdOrderByCreatedAtDesc(b.getId()));
        if (ticketIds.isEmpty()) {
            List<Ticket> all = b.getTickets().stream()
                    .filter(t -> t.getStatus() == TicketStatus.VALID && !claimed.contains(t.getId())).toList();
            if (all.isEmpty()) {
                throw new ApiException(ErrorCode.INVALID_STATE, "There are no tickets left to refund on this booking.");
            }
            return all;
        }
        Map<UUID, Ticket> byId = b.getTickets().stream().collect(Collectors.toMap(Ticket::getId, t -> t));
        FieldErrors errors = FieldErrors.start();
        List<Ticket> chosen = new ArrayList<>();
        for (int i = 0; i < ticketIds.size(); i++) {
            Ticket t = byId.get(ticketIds.get(i));
            String field = "ticketIds[" + i + "]";
            if (t == null) {
                errors.add(field, "is not a ticket on this booking");
            } else if (t.getStatus() == TicketStatus.CHECKED_IN) {
                errors.add(field, "was already used to enter the event");
            } else if (t.getStatus() != TicketStatus.VALID) {
                errors.add(field, "was already refunded or cancelled");
            } else if (claimed.contains(t.getId())) {
                errors.add(field, "already has a refund request");
            } else if (!chosen.contains(t)) {
                chosen.add(t);
            }
        }
        errors.throwIfAny();
        return chosen;
    }

    /** Builds (does not save) a refund row with the tickets' pro-rata share of the booking's price. */
    private Refund row(Booking b, List<Ticket> chosen, RefundKind kind, boolean includesFee, UUID requestedBy,
            String reason, Refund existing) {
        PriceBreakdown share = share(b, chosen);
        Refund r = existing != null ? existing : new Refund();
        r.setBookingId(b.getId());
        r.setPaymentId(payments.findByBookingId(b.getId()).filter(p -> p.getStatus() == PaymentStatus.CAPTURED)
                .map(p -> p.getId()).orElse(null));
        if (r.getPaymentId() == null && b.getTotalPaise() > 0) {
            throw new IllegalStateException("Paid booking " + b.getCode() + " has no captured payment");
        }
        r.setKind(kind);
        r.setFeePaise(includesFee ? share.feePaise() : 0);
        r.setFeeGstPaise(includesFee ? share.feeGstPaise() : 0);
        r.setAmountPaise(share.subtotalPaise() + r.getFeePaise() + r.getFeeGstPaise());
        r.setCommissionPaise(share.commissionPaise());
        r.setTicketIds(chosen.stream().map(Ticket::getId).toArray(UUID[]::new));
        r.setReason(reason);
        r.setRequestedBy(requestedBy);
        return r;
    }

    /** Applies a refund: tickets REFUNDED, inventory back on sale, ledger reversed, row REQUESTED (sent after commit). */
    private Refund apply(Booking b, List<Ticket> chosen, RefundKind kind, boolean includesFee, UUID requestedBy,
            UUID decidedBy, String reason, Refund existing) {
        Refund r = row(b, chosen, kind, includesFee, requestedBy, reason, existing);
        r.setDecidedBy(decidedBy);
        r.setStatus(RefundStatus.REQUESTED);
        r.setFailureReason(null);
        refunds.saveAndFlush(r);

        OffsetDateTime now = OffsetDateTime.now(clock);
        Map<UUID, Integer> perType = new TreeMap<>(Comparator.comparing(UUID::toString));
        for (Ticket t : chosen) {
            t.setStatus(TicketStatus.REFUNDED);
            t.setRefundedAt(now);
            perType.merge(t.getTicketTypeId(), 1, Integer::sum);
        }
        perType.forEach(inventory::returnSold);
        b.setStatus(statusFor(b));
        bookings.saveAndFlush(b);

        UUID organizerId = events.findById(b.getEventId()).orElseThrow().getOrganizer().getId();
        ledger.recordRefund(b, organizerId, r.getAmountPaise() - r.getFeePaise() - r.getFeeGstPaise(),
                r.getCommissionPaise());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("refundId", r.getId().toString());
        details.put("kind", kind.name());
        details.put("amountPaise", r.getAmountPaise());
        details.put("tickets", chosen.size());
        audit.record(decidedBy != null ? decidedBy : requestedBy, "REFUND_APPROVED", "booking", b.getId().toString(),
                null, details);
        return r;
    }

    /**
     * Razorpay refused the refund. For an attendee or organizer refund whose seats are all still free, the tickets
     * become valid again (ledger and inventory restored). Otherwise - the event was cancelled, the money is owed for a
     * late payment, or the seats were resold - the tickets stay refunded and the refund is queued for retry.
     */
    private void afterFailure(UUID refundId) {
        tx.executeWithoutResult(s -> {
            Refund r = refunds.findById(refundId).orElseThrow();
            Booking b = bookings.findForUpdate(r.getBookingId()).orElseThrow();
            Event event = events.findById(b.getEventId()).orElseThrow();
            boolean restorable = r.getTicketIds().length > 0 && r.getKind() != RefundKind.CANCELLATION
                    && r.getKind() != RefundKind.LATE_PAYMENT && event.getStatus() == EventStatus.PUBLISHED;

            Set<UUID> ids = Set.of(r.getTicketIds());
            List<Ticket> tickets = b.getTickets().stream()
                    .filter(t -> ids.contains(t.getId()) && t.getStatus() == TicketStatus.REFUNDED).toList();
            Map<UUID, Integer> perType = new TreeMap<>(Comparator.comparing(UUID::toString));
            tickets.forEach(t -> perType.merge(t.getTicketTypeId(), 1, Integer::sum));
            if (restorable) {
                List<Map.Entry<UUID, Integer>> taken = new ArrayList<>();
                for (Map.Entry<UUID, Integer> e : perType.entrySet()) {
                    if (!inventory.sellWithoutHold(e.getKey(), e.getValue())) {
                        taken.forEach(t -> inventory.returnSold(t.getKey(), t.getValue()));
                        restorable = false;
                        break;
                    }
                    taken.add(e);
                }
            }
            if (!restorable) {
                // The tickets stay refunded; the money is still owed, so keep trying.
                r.setNeedsRetry(true);
                refunds.save(r);
                log.error("Refund {} was refused by Razorpay and is queued for retry", refundId);
                return;
            }
            for (Ticket t : tickets) {
                t.setStatus(TicketStatus.VALID);
                t.setRefundedAt(null);
            }
            b.setStatus(statusFor(b));
            bookings.saveAndFlush(b);
            ledger.recordRefundReversal(b, event.getOrganizer().getId(),
                    r.getAmountPaise() - r.getFeePaise() - r.getFeeGstPaise(), r.getCommissionPaise());
            audit.record(null, "REFUND_REVERSED", "booking", b.getId().toString(), null,
                    Map.of("refundId", refundId.toString(), "tickets", tickets.size()));
        });
    }

    /** Each ticket's pro-rata share (FeeCalculator.split over all tickets in code order), summed for {@code chosen}. */
    static PriceBreakdown share(Booking b, List<Ticket> chosen) {
        Map<UUID, Long> unitPrice = new HashMap<>();
        for (BookingItem i : b.getItems()) {
            unitPrice.put(i.getTicketTypeId(), i.getUnitPricePaise());
        }
        List<Ticket> all = b.getTickets().stream().sorted(Comparator.comparing(Ticket::getCode)).toList();
        PriceBreakdown whole = new PriceBreakdown(b.getSubtotalPaise(), b.getFeePaise(), b.getFeeGstPaise(),
                b.getTotalPaise(), b.getCommissionPaise(), all.size());
        List<PriceBreakdown> parts = FeeCalculator.split(whole, all.stream().map(t -> unitPrice.get(t.getTicketTypeId())).toList());
        Set<UUID> wanted = chosen.stream().map(Ticket::getId).collect(Collectors.toSet());
        long sub = 0, fee = 0, gst = 0, commission = 0;
        for (int i = 0; i < all.size(); i++) {
            if (wanted.contains(all.get(i).getId())) {
                PriceBreakdown p = parts.get(i);
                sub += p.subtotalPaise();
                fee += p.feePaise();
                gst += p.feeGstPaise();
                commission += p.commissionPaise();
            }
        }
        return new PriceBreakdown(sub, fee, gst, sub + fee + gst, commission, chosen.size());
    }

    private static BookingStatus statusFor(Booking b) {
        long live = b.getTickets().stream()
                .filter(t -> t.getStatus() == TicketStatus.VALID || t.getStatus() == TicketStatus.CHECKED_IN).count();
        if (live == b.getTickets().size()) {
            return BookingStatus.CONFIRMED;
        }
        return live == 0 ? BookingStatus.REFUNDED : BookingStatus.PARTIALLY_REFUNDED;
    }

    private Event ownedEvent(UUID organizerId, UUID eventId) {
        Event e = events.findById(eventId).orElseThrow(() -> ApiException.notFound("Event"));
        if (!e.getOrganizer().getId().equals(organizerId)) {
            throw ApiException.notFound("Event");
        }
        return e;
    }

    private Refund pendingForOrganizer(UUID organizerId, UUID refundId) {
        Refund r = refunds.findById(refundId).orElseThrow(() -> ApiException.notFound("Refund"));
        Booking b = bookings.findById(r.getBookingId()).orElseThrow();
        Event e = events.findById(b.getEventId()).orElseThrow();
        if (!e.getOrganizer().getId().equals(organizerId)) {
            throw ApiException.notFound("Refund");
        }
        if (r.getStatus() != RefundStatus.PENDING_APPROVAL) {
            throw new ApiException(ErrorCode.INVALID_STATE, "This refund request was already decided.");
        }
        if (e.getStatus() == EventStatus.CANCELLED) {
            throw new ApiException(ErrorCode.INVALID_STATE, "The event was cancelled; every ticket is refunded automatically.");
        }
        return r;
    }

    private OrganizerRefundView view(UUID organizerId, UUID eventId, UUID refundId) {
        return listForEvent(organizerId, eventId).stream().filter(v -> v.id().equals(refundId)).findFirst().orElseThrow();
    }
}

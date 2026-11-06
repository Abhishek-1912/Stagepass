package com.stagepass.booking.service;

import com.stagepass.booking.domain.Refund;
import com.stagepass.booking.domain.RefundStatus;
import com.stagepass.catalog.domain.Event;
import com.stagepass.catalog.domain.EventStatus;
import com.stagepass.catalog.domain.RefundPolicy;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Whether an attendee can refund on their own right now (spec 8.4a), and on what terms. */
@Component
public class RefundEligibility {

    public static final String RESCHEDULE = "RESCHEDULE";
    public static final String ATTENDEE_REQUEST = "ATTENDEE_REQUEST";
    public static final String NONE = "NONE";

    /**
     * @param selfServe   refund happens immediately on request
     * @param requestable a request may be sent (to the organizer when not self-serve)
     */
    public record Eligibility(boolean selfServe, boolean requestable, String kind, boolean includesFee,
            OffsetDateTime until, String reason) {
    }

    /** Refund states that still claim their tickets (so the same ticket can't be refunded twice). */
    public static final Set<RefundStatus> OPEN = EnumSet.of(RefundStatus.PENDING_APPROVAL, RefundStatus.REQUESTED,
            RefundStatus.PROCESSING);

    /** Ticket ids already claimed by a pending or in-flight refund. */
    public static Set<UUID> claimedTicketIds(List<Refund> refunds) {
        Set<UUID> ids = new HashSet<>();
        refunds.stream().filter(r -> OPEN.contains(r.getStatus()) || r.getStatus() == RefundStatus.PROCESSED)
                .forEach(r -> ids.addAll(Arrays.asList(r.getTicketIds())));
        return ids;
    }

    private final FeeSettingsRepository fees;

    public RefundEligibility(FeeSettingsRepository fees) {
        this.fees = fees;
    }

    public Eligibility forEvent(Event e, OffsetDateTime now) {
        if (e.getStatus() == EventStatus.CANCELLED) {
            return new Eligibility(false, false, NONE, true, null, "The event was cancelled - refunds are automatic.");
        }
        if (e.getStatus() != EventStatus.PUBLISHED || !now.isBefore(e.getStartsAt())) {
            return new Eligibility(false, false, NONE, false, null, "Refunds close when the event starts.");
        }
        if (e.getRescheduleRefundUntil() != null && now.isBefore(e.getRescheduleRefundUntil())) {
            return new Eligibility(true, true, RESCHEDULE, true, e.getRescheduleRefundUntil(),
                    "The event changed - you can cancel for a full refund, fees included.");
        }
        if (e.getRefundPolicy() == RefundPolicy.UNTIL_HOURS_BEFORE && e.getRefundCutoffHours() != null) {
            OffsetDateTime until = e.getStartsAt().minusHours(e.getRefundCutoffHours());
            if (now.isBefore(until)) {
                boolean fee = fees.forEvent(e.getId()).refundFeeOnAttendeeRequest();
                return new Eligibility(true, true, ATTENDEE_REQUEST, fee, until, fee
                        ? "Refundable in full until " + e.getRefundCutoffHours() + " hours before the event."
                        : "Ticket price refundable until " + e.getRefundCutoffHours()
                                + " hours before the event (convenience fee is not refunded).");
            }
        }
        return new Eligibility(false, true, NONE, false, null,
                "Outside the refund policy - the organizer will review your request.");
    }
}

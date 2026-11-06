package com.stagepass.booking.service;

import com.stagepass.catalog.service.EventCancellationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/** When a published event is cancelled, refund every ticket once the cancellation has committed. */
@Component
public class CancellationRefunds implements EventCancellationHandler {

    private static final Logger log = LoggerFactory.getLogger(CancellationRefunds.class);

    private final RefundService refunds;

    public CancellationRefunds(RefundService refunds) {
        this.refunds = refunds;
    }

    @Override
    public void onEventCancelled(UUID eventId, UUID actorId) {
        Runnable work = () -> {
            int n = refunds.refundEventCancellation(eventId, actorId);
            log.info("Event {} cancelled: {} bookings refunded", eventId, n);
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    work.run();
                }
            });
        } else {
            work.run();
        }
    }
}

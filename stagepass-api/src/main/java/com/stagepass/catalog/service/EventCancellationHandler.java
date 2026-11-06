package com.stagepass.catalog.service;

import java.util.UUID;

/**
 * Notified inside the transaction that cancels a published event (refunds, emails). Implementations must defer
 * slow or external work until after commit.
 */
public interface EventCancellationHandler {

    void onEventCancelled(UUID eventId, UUID actorId);
}

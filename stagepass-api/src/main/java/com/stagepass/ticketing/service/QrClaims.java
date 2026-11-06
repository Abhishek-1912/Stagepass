package com.stagepass.ticketing.service;

import java.util.UUID;

/** What a valid QR token says: which ticket, for which event, with which nonce (compare to tickets.qr_nonce). */
public record QrClaims(UUID ticketId, UUID eventId, String nonce) {
}

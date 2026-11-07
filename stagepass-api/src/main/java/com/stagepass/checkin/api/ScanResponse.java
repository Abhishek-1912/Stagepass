package com.stagepass.checkin.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * The answer shown at the door. {@code result}: VALID, ALREADY_USED, WRONG_GATE, WRONG_EVENT or INVALID. Only the
 * scanned ticket's code, holder and type are disclosed.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScanResponse(String result, String message, TicketInfo ticket, FirstScan firstScan,
        List<String> allowedGates, Counts counts) {

    public record TicketInfo(String code, String holderName, String ticketType) {
    }

    public record FirstScan(OffsetDateTime at, String gateName, String scannerName, boolean byYou) {
    }

    public record Counts(int checkedIn, int total) {
    }
}

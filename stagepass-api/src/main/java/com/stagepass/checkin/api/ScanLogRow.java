package com.stagepass.checkin.api;

import java.time.OffsetDateTime;

/** One scan at the door, for the organizer's log. */
public record ScanLogRow(OffsetDateTime at, String result, String ticketCode, String holderName, String gateName,
        String scannerName, boolean manual) {
}

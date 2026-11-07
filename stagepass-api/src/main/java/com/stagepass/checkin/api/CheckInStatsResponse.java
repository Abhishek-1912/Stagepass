package com.stagepass.checkin.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Live door counts: overall, per gate (plus a "No gate" row when used) and per scanner. */
public record CheckInStatsResponse(int checkedIn, int total, List<GateCount> byGate, List<ScannerCount> byScanner,
        OffsetDateTime lastScanAt) {

    public record GateCount(UUID gateId, String name, int checkedIn) {
    }

    public record ScannerCount(String name, int checkedIn) {
    }
}

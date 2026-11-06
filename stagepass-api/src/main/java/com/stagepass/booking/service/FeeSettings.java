package com.stagepass.booking.service;

/** Effective fee rules for one event (spec §8.2). Basis points: 250 = 2.5%. */
public record FeeSettings(int convenienceFeeBps, long convenienceFeeFlatPaise, int gstBps, int commissionBps,
        boolean refundFeeOnAttendeeRequest) {
}

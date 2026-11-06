package com.stagepass.booking.service;

import com.stagepass.booking.domain.PriceBreakdown;

import java.util.ArrayList;
import java.util.List;

/**
 * Prices bookings (spec §8.2). Integer paise only; every step rounds half-up to the paisa.
 * fee = round(subtotal × bps) + flat × tickets; GST applies to the fee only; free orders pay nothing.
 */
public final class FeeCalculator {

    private FeeCalculator() {
    }

    public static PriceBreakdown price(long subtotalPaise, int ticketCount, FeeSettings s) {
        if (subtotalPaise == 0) {
            return new PriceBreakdown(0, 0, 0, 0, 0, ticketCount);
        }
        long fee = halfUp(subtotalPaise * s.convenienceFeeBps(), 10_000) + s.convenienceFeeFlatPaise() * ticketCount;
        long gst = halfUp(fee * s.gstBps(), 10_000);
        long commission = halfUp(subtotalPaise * s.commissionBps(), 10_000);
        return new PriceBreakdown(subtotalPaise, fee, gst, subtotalPaise + fee + gst, commission, ticketCount);
    }

    /** Half-up division for non-negative values: halfUp(15, 10) = 2. */
    public static long halfUp(long numerator, long denominator) {
        return (numerator * 2 + denominator) / (denominator * 2);
    }

    /**
     * Splits a booking's price into one breakdown per ticket, pro-rata by ticket price (equal shares for free
     * tickets). Each component of the parts sums exactly to the whole; the last ticket absorbs rounding.
     */
    public static List<PriceBreakdown> split(PriceBreakdown whole, List<Long> ticketPrices) {
        int n = ticketPrices.size();
        long weightTotal = ticketPrices.stream().mapToLong(Long::longValue).sum();
        long[] weights = ticketPrices.stream().mapToLong(p -> weightTotal == 0 ? 1 : p).toArray();
        long weightSum = weightTotal == 0 ? n : weightTotal;

        long[] fee = allocate(whole.feePaise(), weights, weightSum);
        long[] gst = allocate(whole.feeGstPaise(), weights, weightSum);
        long[] commission = allocate(whole.commissionPaise(), weights, weightSum);
        List<PriceBreakdown> parts = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            long subtotal = ticketPrices.get(i);
            parts.add(new PriceBreakdown(subtotal, fee[i], gst[i], subtotal + fee[i] + gst[i], commission[i], 1));
        }
        return parts;
    }

    /**
     * Splits {@code amount} by weight with the largest-remainder method: every share is the floor of its exact
     * value, and the paise left over go one each to the largest remainders (earlier tickets win ties). Shares are
     * never negative and always sum to {@code amount}.
     */
    private static long[] allocate(long amount, long[] weights, long weightSum) {
        int n = weights.length;
        long[] out = new long[n];
        long[] remainder = new long[n];
        long used = 0;
        for (int i = 0; i < n; i++) {
            out[i] = amount * weights[i] / weightSum;
            remainder[i] = amount * weights[i] % weightSum;
            used += out[i];
        }
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (x, y) -> remainder[x] != remainder[y] ? Long.compare(remainder[y], remainder[x]) : Integer.compare(x, y));
        for (int k = 0; used < amount; k++, used++) {
            out[order[k % n]]++;
        }
        return out;
    }
}

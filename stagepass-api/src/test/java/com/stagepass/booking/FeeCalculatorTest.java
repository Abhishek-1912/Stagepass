package com.stagepass.booking;

import com.stagepass.booking.domain.PriceBreakdown;
import com.stagepass.booking.service.FeeCalculator;
import com.stagepass.booking.service.FeeSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeeCalculatorTest {

    static final FeeSettings DEFAULTS = new FeeSettings(250, 1000, 1800, 0, false);

    @Test
    void twoTicketsAt499() {
        assertThat(FeeCalculator.price(99800, 2, DEFAULTS))
                .isEqualTo(new PriceBreakdown(99800, 4495, 809, 105104, 0, 2));
    }

    @Test
    void halfUpRoundingAtEachStep() {
        PriceBreakdown p = FeeCalculator.price(37035, 3, DEFAULTS);
        assertThat(p.feePaise()).isEqualTo(3926);
        assertThat(p.feeGstPaise()).isEqualTo(707);
        assertThat(p.totalPaise()).isEqualTo(41668);
    }

    @Test
    void freeOrderHasNoFees() {
        assertThat(FeeCalculator.price(0, 4, DEFAULTS)).isEqualTo(new PriceBreakdown(0, 0, 0, 0, 0, 4));
    }

    @Test
    void commissionIsAppliedToSubtotal() {
        FeeSettings withCommission = new FeeSettings(250, 1000, 1800, 500, false);
        assertThat(FeeCalculator.price(99800, 2, withCommission).commissionPaise()).isEqualTo(4990);
    }

    @Test
    void halfUpRoundsExactHalvesUp() {
        assertThat(FeeCalculator.halfUp(5, 10)).isEqualTo(1);   // 0.5 → 1
        assertThat(FeeCalculator.halfUp(14, 10)).isEqualTo(1);  // 1.4 → 1
        assertThat(FeeCalculator.halfUp(15, 10)).isEqualTo(2);  // 1.5 → 2
    }

    @Test
    void splitReconcilesExactly() {
        FeeSettings withCommission = new FeeSettings(250, 1000, 1800, 333, false);
        PriceBreakdown whole = FeeCalculator.price(3 * 12345, 3, withCommission);
        List<PriceBreakdown> parts = FeeCalculator.split(whole, List.of(12345L, 12345L, 12345L));

        assertThat(parts).hasSize(3);
        assertThat(parts.stream().mapToLong(PriceBreakdown::subtotalPaise).sum()).isEqualTo(whole.subtotalPaise());
        assertThat(parts.stream().mapToLong(PriceBreakdown::feePaise).sum()).isEqualTo(whole.feePaise());
        assertThat(parts.stream().mapToLong(PriceBreakdown::feeGstPaise).sum()).isEqualTo(whole.feeGstPaise());
        assertThat(parts.stream().mapToLong(PriceBreakdown::totalPaise).sum()).isEqualTo(whole.totalPaise());
        assertThat(parts.stream().mapToLong(PriceBreakdown::commissionPaise).sum()).isEqualTo(whole.commissionPaise());
        assertThat(parts).allSatisfy(p -> assertThat(p.ticketCount()).isEqualTo(1));
    }

    @Test
    void splitIsProRataByTicketPrice() {
        PriceBreakdown whole = FeeCalculator.price(49900 + 149900, 2, DEFAULTS);
        List<PriceBreakdown> parts = FeeCalculator.split(whole, List.of(49900L, 149900L));
        assertThat(parts.get(0).subtotalPaise()).isEqualTo(49900);
        assertThat(parts.get(1).subtotalPaise()).isEqualTo(149900);
        assertThat(parts.get(0).feePaise()).isLessThan(parts.get(1).feePaise());
    }

    @Test
    void splitNeverGoesNegative() {
        PriceBreakdown whole = new PriceBreakdown(500, 3, 3, 506, 3, 5);
        List<PriceBreakdown> parts = FeeCalculator.split(whole, List.of(100L, 100L, 100L, 100L, 100L));
        assertThat(parts).allSatisfy(p -> {
            assertThat(p.feePaise()).isBetween(0L, 1L);
            assertThat(p.commissionPaise()).isBetween(0L, 1L);
        });
        assertThat(parts.stream().mapToLong(PriceBreakdown::feePaise).sum()).isEqualTo(3);
    }
}

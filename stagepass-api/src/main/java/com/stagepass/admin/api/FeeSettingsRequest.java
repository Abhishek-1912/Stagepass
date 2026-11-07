package com.stagepass.admin.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Platform fee rules (spec 8.2). Basis points: 250 = 2.5%. */
public record FeeSettingsRequest(@NotNull @Min(0) @Max(2000) Integer convenienceFeeBps,
        @NotNull @Min(0) @Max(100000) Long convenienceFeeFlatPaise, @NotNull @Min(0) @Max(2800) Integer gstBps,
        @NotNull @Min(0) @Max(5000) Integer commissionBps, @NotNull Boolean refundFeeOnAttendeeRequest) {
}

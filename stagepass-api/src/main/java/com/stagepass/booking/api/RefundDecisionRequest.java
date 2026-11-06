package com.stagepass.booking.api;

import jakarta.validation.constraints.Size;

public record RefundDecisionRequest(@Size(max = 500) String note) {
}

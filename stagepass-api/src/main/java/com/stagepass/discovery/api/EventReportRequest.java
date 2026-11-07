package com.stagepass.discovery.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code reason}: SCAM, MISLEADING, OFFENSIVE, DUPLICATE or OTHER (OTHER needs details). */
public record EventReportRequest(@NotBlank String reason, @Size(max = 1000) String details) {
}

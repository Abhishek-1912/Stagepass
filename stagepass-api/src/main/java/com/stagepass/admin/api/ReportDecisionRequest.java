package com.stagepass.admin.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReportDecisionRequest(@NotBlank @Size(min = 3, max = 500) String note) {
}

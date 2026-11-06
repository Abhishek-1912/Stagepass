package com.stagepass.admin.api;

import jakarta.validation.constraints.NotNull;

public record FeatureRequest(@NotNull Boolean featured) {
}

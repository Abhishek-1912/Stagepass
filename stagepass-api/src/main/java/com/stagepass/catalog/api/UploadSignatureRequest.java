package com.stagepass.catalog.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** {@code purpose} is EVENT_IMAGE (needs eventId) or ORGANIZER_LOGO. */
public record UploadSignatureRequest(@NotNull Purpose purpose, UUID eventId) {

    public enum Purpose { EVENT_IMAGE, ORGANIZER_LOGO }
}

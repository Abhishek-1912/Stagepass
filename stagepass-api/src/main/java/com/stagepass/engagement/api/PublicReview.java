package com.stagepass.engagement.api;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A review as anyone sees it: first name only, never an email. */
public record PublicReview(UUID id, int rating, String comment, String authorName, OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}

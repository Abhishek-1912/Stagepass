package com.stagepass.catalog.api;

import com.stagepass.common.validation.Formats;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Create or partial update of an event. On create, title, categoryId, startsAt and endsAt are required
 * (checked in the service so PATCH can omit them). Image URLs are additionally checked against the event's
 * Cloudinary folder.
 */
public record EventUpsertRequest(
        @Size(min = 3, max = 140) String title,
        @Size(max = 280) String summary,
        @Size(max = 20000) String description,
        UUID categoryId,
        UUID venueId,
        Boolean online,
        @URL @Size(max = 500) String onlineUrl,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        @Size(max = 10000) String terms,
        @Min(1) @Max(50) Integer maxTicketsPerUser,
        String refundPolicy,
        @Min(0) @Max(720) Integer refundCutoffHours,
        @Size(max = 500) String coverImageUrl,
        @Size(max = 8) List<@Size(max = 500) String> gallery,
        /** Optional fields to reset to empty, e.g. ["maxTicketsPerUser","terms"]. */
        @Size(max = 10) List<String> clear) {

    public EventUpsertRequest {
        title = Formats.trim(title);
        summary = Formats.trim(summary);
        onlineUrl = Formats.trim(onlineUrl);
        refundPolicy = Formats.upper(refundPolicy);
        coverImageUrl = Formats.trim(coverImageUrl);
    }
}

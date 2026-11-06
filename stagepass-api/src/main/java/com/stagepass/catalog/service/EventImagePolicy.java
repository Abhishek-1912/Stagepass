package com.stagepass.catalog.service;

import com.stagepass.common.error.FieldErrors;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Event images must be Cloudinary uploads in that event's own folder. */
@Component
public class EventImagePolicy {

    private final ImageUrlPolicy urls;

    public EventImagePolicy(ImageUrlPolicy urls) {
        this.urls = urls;
    }

    public void check(UUID eventId, String url, String field, FieldErrors errors) {
        errors.addIf(!urls.isOwned(url, ImageUrlPolicy.eventFolder(eventId)), field,
                "Upload the image through Stagepass (jpg, png or webp)");
    }
}

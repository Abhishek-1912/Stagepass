package com.stagepass.catalog.service;

import com.stagepass.common.error.FieldErrors;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Decides whether an image URL may be attached to an event. Task 9 replaces the https-only check with the
 * Cloudinary folder lock.
 */
@Component
public class EventImagePolicy {

    public void check(UUID eventId, String url, String field, FieldErrors errors) {
        errors.addIf(!url.startsWith("https://"), field, "must be an https image URL");
    }
}

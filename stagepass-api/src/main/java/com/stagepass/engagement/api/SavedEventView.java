package com.stagepass.engagement.api;

import com.stagepass.discovery.api.PublicEventCard;

import java.time.OffsetDateTime;

/** A saved event: the public card plus its current status (it may have ended or been cancelled since). */
public record SavedEventView(PublicEventCard event, String status, OffsetDateTime savedAt) {
}

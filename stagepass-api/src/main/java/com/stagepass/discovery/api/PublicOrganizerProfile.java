package com.stagepass.discovery.api;

import java.util.List;

public record PublicOrganizerProfile(PublicOrganizer organizer, List<PublicEventCard> upcoming, int pastEvents) {
}

package com.stagepass.discovery.api;

import com.stagepass.discovery.service.PublicEventService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
public class PublicOrganizerController {

    private final PublicEventService events;

    public PublicOrganizerController(PublicEventService events) {
        this.events = events;
    }

    @GetMapping("/api/v1/public/organizers/{slug}")
    public ResponseEntity<PublicOrganizerProfile> profile(@PathVariable String slug) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
                .body(events.organizer(slug));
    }
}

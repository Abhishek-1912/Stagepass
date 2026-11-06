package com.stagepass.organizer.api;

import com.stagepass.common.api.PageResponse;
import com.stagepass.common.security.CurrentUser;
import com.stagepass.common.security.OrganizerGuard;
import com.stagepass.organizer.service.OrganizerBookingQueries;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Bookings, attendees and the attendee CSV for one of the organizer's events. */
@RestController
@RequestMapping("/api/v1/organizer/events/{eventId}")
public class OrganizerEventDataController {

    private final OrganizerBookingQueries queries;
    private final OrganizerGuard guard;
    private final CurrentUser currentUser;

    public OrganizerEventDataController(OrganizerBookingQueries queries, OrganizerGuard guard, CurrentUser currentUser) {
        this.queries = queries;
        this.guard = guard;
        this.currentUser = currentUser;
    }

    @GetMapping("/bookings")
    public PageResponse<OrganizerBookingRow> bookings(@PathVariable UUID eventId, @RequestParam(required = false) String status,
            @RequestParam(required = false) String q, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return queries.bookings(guard.requireApprovedOrganizerId(), eventId, status, q, page, size);
    }

    @GetMapping("/attendees")
    public PageResponse<AttendeeRow> attendees(@PathVariable UUID eventId, @RequestParam(required = false) String status,
            @RequestParam(required = false) String q, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return queries.attendees(guard.requireApprovedOrganizerId(), eventId, status, q, page, size);
    }

    @GetMapping("/attendees.csv")
    public void attendeesCsv(@PathVariable UUID eventId, HttpServletResponse response) throws IOException {
        UUID organizerId = guard.requireApprovedOrganizerId();
        OrganizerBookingQueries.OwnedEvent event = queries.owned(organizerId, eventId);
        response.setContentType("text/csv");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(queries.csvFilename(event)).build().toString());
        Writer out = response.getWriter();
        queries.writeAttendeesCsv(organizerId, currentUser.id(), eventId, out);
        out.flush();
    }
}

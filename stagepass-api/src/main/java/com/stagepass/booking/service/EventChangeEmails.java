package com.stagepass.booking.service;

import com.stagepass.catalog.domain.Event;
import com.stagepass.catalog.service.EventChangeHandler;
import org.springframework.stereotype.Component;

/** Emails ticket holders when a live event's date, time or venue changes (spec 8.4a). */
@Component
public class EventChangeEmails implements EventChangeHandler {

    private final BookingEmails emails;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public EventChangeEmails(BookingEmails emails, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.emails = emails;
        this.jdbc = jdbc;
    }

    /** Also clears sent reminders, so a new start time gets its own 24-hour reminder (same time: dedupe stops it). */
    @Override
    public void onEventChanged(Event event) {
        emails.eventChanged(event);
        jdbc.update("update bookings set reminder_sent_at = null where event_id = ? and reminder_sent_at is not null",
                event.getId());
    }
}

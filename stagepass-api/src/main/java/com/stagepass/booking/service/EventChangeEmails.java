package com.stagepass.booking.service;

import com.stagepass.catalog.domain.Event;
import com.stagepass.catalog.service.EventChangeHandler;
import org.springframework.stereotype.Component;

/** Emails ticket holders when a live event's date, time or venue changes (spec 8.4a). */
@Component
public class EventChangeEmails implements EventChangeHandler {

    private final BookingEmails emails;

    public EventChangeEmails(BookingEmails emails) {
        this.emails = emails;
    }

    @Override
    public void onEventChanged(Event event) {
        emails.eventChanged(event);
    }
}

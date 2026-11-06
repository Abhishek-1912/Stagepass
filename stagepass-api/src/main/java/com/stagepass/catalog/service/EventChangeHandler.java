package com.stagepass.catalog.service;

import com.stagepass.catalog.domain.Event;

/** Notified inside the transaction that changes a live event's date, time or venue (after the refund window opens). */
public interface EventChangeHandler {

    void onEventChanged(Event event);
}

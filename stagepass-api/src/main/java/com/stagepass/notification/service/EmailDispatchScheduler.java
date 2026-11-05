package com.stagepass.notification.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Polls the email outbox. Disable with {@code stagepass.email.dispatcher-enabled=false} (tests do). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "stagepass.email.dispatcher-enabled", havingValue = "true", matchIfMissing = true)
public class EmailDispatchScheduler {

    private final EmailDispatcher dispatcher;

    public EmailDispatchScheduler(EmailDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(initialDelayString = "${stagepass.email.initial-delay:10s}",
            fixedDelayString = "${stagepass.email.poll-interval:15s}")
    void dispatch() {
        dispatcher.dispatchBatch();
    }
}

package com.stagepass.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on all @Scheduled jobs (email outbox, event completion…). Tests set stagepass.scheduling.enabled=false. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "stagepass.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}

package com.stagepass.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Resend HTTP API settings, bound from {@code resend.*}. A blank API key keeps emails queued instead of sending. */
@ConfigurationProperties(prefix = "resend")
public record ResendProperties(
        String apiKey,
        @DefaultValue("Stagepass <onboarding@resend.dev>") String from,
        @DefaultValue("https://api.resend.com") String baseUrl) {

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}

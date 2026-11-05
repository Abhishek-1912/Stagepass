package com.stagepass.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/** Application-level settings bound from {@code stagepass.*}. */
@ConfigurationProperties(prefix = "stagepass")
public record StagepassProperties(
        String webBaseUrl,
        @DefaultValue Cors cors,
        @DefaultValue Bootstrap bootstrap,
        @DefaultValue("10") int holdMinutes,
        String qrSecret,
        String payoutEncryptionKey,
        @DefaultValue Payout payout) {

    public record Cors(@DefaultValue List<String> allowedOrigins) {
    }

    public record Bootstrap(@DefaultValue List<String> adminEmails) {
    }

    public record Payout(@DefaultValue("3") int holdDays) {
    }
}

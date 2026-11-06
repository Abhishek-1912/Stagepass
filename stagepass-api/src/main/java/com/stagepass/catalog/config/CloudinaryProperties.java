package com.stagepass.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Cloudinary account used for signed direct uploads, bound from {@code cloudinary.*}. */
@ConfigurationProperties(prefix = "cloudinary")
public record CloudinaryProperties(String cloudName, String apiKey, String apiSecret) {

    public boolean configured() {
        return notBlank(cloudName) && notBlank(apiKey) && notBlank(apiSecret);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}

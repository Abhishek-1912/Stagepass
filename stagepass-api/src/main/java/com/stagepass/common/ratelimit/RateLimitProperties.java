package com.stagepass.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code stagepass.rate-limits.enabled}: on by default; tests turn it off. The limits themselves are in {@link RateLimitRule}. */
@ConfigurationProperties("stagepass.rate-limits")
public record RateLimitProperties(@DefaultValue("true") boolean enabled) {
}

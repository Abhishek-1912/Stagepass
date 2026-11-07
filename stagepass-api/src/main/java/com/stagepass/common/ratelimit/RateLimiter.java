package com.stagepass.common.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** In-memory token buckets per (rule, key); fine for the single API instance at launch (spec 11). */
@Component
public class RateLimiter {

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();

    public ConsumptionProbe tryConsume(RateLimitRule rule, String key) {
        Bucket bucket = buckets.get(rule.name() + ":" + key, k -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(rule.capacity()).refillIntervally(rule.capacity(), rule.period()).build())
                .build());
        return bucket.tryConsumeAndReturnRemaining(1);
    }
}

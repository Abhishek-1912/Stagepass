package com.stagepass.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.AntPathMatcher;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** The rate limits (spec 11): which requests, keyed by user or IP, and how many per period. */
public enum RateLimitRule {

    BOOKING(Key.USER, 10, List.of("POST /api/v1/bookings")),
    VERIFY(Key.USER, 20, List.of("POST /api/v1/payments/verify")),
    REFUND(Key.USER, 10, List.of("POST /api/v1/me/bookings/*/refunds")),
    SCAN(Key.USER, 120, List.of("POST /api/v1/checkin/scan")),
    WRITE(Key.USER, 20, List.of("POST /api/v1/events/*/reports", "PUT /api/v1/events/*/review",
            "POST /api/v1/uploads/signature", "POST /api/v1/organizer-applications")),
    PUBLIC(Key.IP, 120, List.of("GET /api/v1/public/**", "POST /api/v1/public/quote"));

    public enum Key { USER, IP }

    private static final AntPathMatcher PATHS = new AntPathMatcher();

    private final Key key;
    private final int perMinute;
    private final List<String> patterns;

    RateLimitRule(Key key, int perMinute, List<String> patterns) {
        this.key = key;
        this.perMinute = perMinute;
        this.patterns = patterns;
    }

    public Key key() {
        return key;
    }

    public int capacity() {
        return perMinute;
    }

    public Duration period() {
        return Duration.ofMinutes(1);
    }

    public static Optional<RateLimitRule> match(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return Arrays.stream(values()).filter(r -> r.patterns.stream().anyMatch(p -> {
            int space = p.indexOf(' ');
            return p.substring(0, space).equals(method) && PATHS.match(p.substring(space + 1), path);
        })).findFirst();
    }
}

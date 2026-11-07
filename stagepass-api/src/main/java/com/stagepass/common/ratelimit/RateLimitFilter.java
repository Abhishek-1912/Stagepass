package com.stagepass.common.ratelimit;

import com.stagepass.common.error.ErrorCode;
import com.stagepass.common.error.ProblemWriter;
import com.stagepass.common.security.StagepassPrincipal;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Applies {@link RateLimitRule}s after authentication, so signed-in requests are counted per user and the rest per
 * client IP ({@code server.forward-headers-strategy=framework} makes {@code getRemoteAddr} the real client on Render).
 * Not a Spring bean on its own: SecurityConfig adds it to the security chain, so it runs exactly once.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    static final String DETAIL = "Too many requests. Please wait a moment and try again.";

    private final RateLimiter limiter;
    private final ProblemWriter problems;
    private final RateLimitProperties props;

    public RateLimitFilter(RateLimiter limiter, ProblemWriter problems, RateLimitProperties props) {
        this.limiter = limiter;
        this.problems = problems;
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<RateLimitRule> rule = props.enabled() ? RateLimitRule.match(request) : Optional.empty();
        if (rule.isPresent()) {
            ConsumptionProbe probe = limiter.tryConsume(rule.get(), key(rule.get(), request));
            if (!probe.isConsumed()) {
                long seconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
                response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(seconds));
                problems.write(request, response, ErrorCode.RATE_LIMITED, DETAIL);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static String key(RateLimitRule rule, HttpServletRequest request) {
        if (rule.key() == RateLimitRule.Key.USER) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof StagepassPrincipal p) {
                return "user:" + p.userId();
            }
        }
        return "ip:" + request.getRemoteAddr();
    }
}

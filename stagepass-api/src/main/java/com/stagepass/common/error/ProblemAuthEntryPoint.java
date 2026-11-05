package com.stagepass.common.error;

import com.stagepass.common.security.StagepassAuthenticationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Answers authentication failures in Problem Details form: 401 for missing/invalid tokens, or the specific
 * code (e.g. ACCOUNT_SUSPENDED, ACCOUNT_CONFLICT) raised while resolving the account.
 */
@Component
public class ProblemAuthEntryPoint implements AuthenticationEntryPoint {

    private final ProblemWriter writer;

    public ProblemAuthEntryPoint(ProblemWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        if (authException instanceof StagepassAuthenticationException sae) {
            writer.write(request, response, sae.code(), sae.getMessage());
            return;
        }
        response.setHeader("WWW-Authenticate", "Bearer");
        writer.write(request, response, ErrorCode.UNAUTHENTICATED, "Authentication is required");
    }
}

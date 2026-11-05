package com.stagepass.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 401 responses for missing or invalid tokens, in Problem Details form. */
@Component
public class ProblemAuthEntryPoint implements AuthenticationEntryPoint {

    private final ProblemWriter writer;

    public ProblemAuthEntryPoint(ProblemWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        writer.write(request, response, ErrorCode.UNAUTHENTICATED, "Authentication is required");
    }
}

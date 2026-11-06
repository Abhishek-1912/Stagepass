package com.stagepass.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 403 responses for authenticated users lacking a role, in Problem Details form. The organizer area answers
 * {@code ORGANIZER_NOT_APPROVED} so the web app can point people to the organizer application.
 */
@Component
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemWriter writer;

    public ProblemAccessDeniedHandler(ProblemWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        if (request.getRequestURI().startsWith("/api/v1/organizer/")) {
            writer.write(request, response, ErrorCode.ORGANIZER_NOT_APPROVED, "This area is for approved organizers.");
            return;
        }
        writer.write(request, response, ErrorCode.FORBIDDEN, "You do not have access to this resource");
    }
}

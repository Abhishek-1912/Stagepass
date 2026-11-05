package com.stagepass.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Writes a Problem Details body directly to the servlet response (for filters and security handlers). */
@Component
public class ProblemWriter {

    private final ObjectMapper mapper;

    public ProblemWriter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String detail)
            throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", code.status().getReasonPhrase());
        body.put("status", code.status().value());
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());
        body.put("code", code.name());
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), body);
    }
}

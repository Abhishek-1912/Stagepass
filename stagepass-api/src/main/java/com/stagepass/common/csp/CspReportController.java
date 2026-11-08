package com.stagepass.common.csp;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Receives browsers' Content-Security-Policy violation reports (legacy {@code csp-report} objects and the Reporting
 * API's arrays) and logs one short WARN line each, so a policy that blocks something real shows up in the API logs.
 */
@RestController
public class CspReportController {

    private static final Logger log = LoggerFactory.getLogger(CspReportController.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    static final int MAX_BYTES = 8 * 1024;
    private static final int MAX_FIELD = 200;

    @PostMapping("/api/v1/csp-reports")
    public ResponseEntity<Void> receive(HttpServletRequest request) throws IOException {
        byte[] body;
        try (InputStream in = request.getInputStream()) {
            body = in.readNBytes(MAX_BYTES + 1);
        }
        if (body.length > MAX_BYTES) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }
        try {
            JsonNode root = JSON.readTree(new String(body, StandardCharsets.UTF_8));
            if (root != null && root.isArray()) {
                root.forEach(r -> logReport(r.path("body"), r.path("url").asString("")));
            } else if (root != null && root.has("csp-report")) {
                JsonNode r = root.get("csp-report");
                logReport(r, r.path("document-uri").asString(""));
            }
        } catch (RuntimeException e) {
            // Not JSON or an unexpected shape: nothing useful to log; browsers don't need an error back.
        }
        return ResponseEntity.noContent().build();
    }

    private static void logReport(JsonNode r, String document) {
        String blocked = first(r, "blocked-uri", "blockedURL");
        String directive = first(r, "violated-directive", "effectiveDirective");
        if (blocked.isEmpty() && directive.isEmpty()) {
            return;
        }
        log.warn("CSP violation: blocked={} directive={} page={}", clean(blocked), clean(directive), clean(document));
    }

    private static String first(JsonNode r, String a, String b) {
        String v = r.path(a).asString("");
        return v.isEmpty() ? r.path(b).asString("") : v;
    }

    /** One line, at most 200 characters. */
    private static String clean(String s) {
        String one = s.replaceAll("[\\r\\n\\t]", " ");
        return one.length() > MAX_FIELD ? one.substring(0, MAX_FIELD) : one;
    }
}

package com.stagepass.common;

import com.stagepass.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "stagepass.rate-limits.enabled=true")
class CspReportTest extends PostgresIntegrationTest {

    private static RequestPostProcessor from(String ip) {
        return r -> {
            r.setRemoteAddr(ip);
            return r;
        };
    }

    private static String ip() {
        return "10.9." + (UUID.randomUUID().hashCode() & 0xff) + "." + (UUID.randomUUID().hashCode() & 0xff);
    }

    @Test
    void legacyReportIsLoggedAndAnswered204(CapturedOutput output) throws Exception {
        mvc.perform(post("/api/v1/csp-reports").with(from(ip())).contentType("application/csp-report")
                        .content("{\"csp-report\":{\"document-uri\":\"https://stagepass-web.onrender.com/\","
                                + "\"blocked-uri\":\"https://evil.example/x.js\",\"violated-directive\":\"script-src\"}}"))
                .andExpect(status().isNoContent());
        assertThat(output.getOut()).contains("https://evil.example/x.js").contains("script-src");
    }

    @Test
    void reportingApiArrayIsAccepted(CapturedOutput output) throws Exception {
        mvc.perform(post("/api/v1/csp-reports").with(from(ip())).contentType("application/reports+json")
                        .content("[{\"type\":\"csp-violation\",\"url\":\"https://stagepass-web.onrender.com/about\","
                                + "\"body\":{\"blockedURL\":\"https://cdn.example/img.png\",\"effectiveDirective\":\"img-src\"}}]"))
                .andExpect(status().isNoContent());
        assertThat(output.getOut()).contains("https://cdn.example/img.png").contains("img-src");
    }

    @Test
    void garbageAndHugeBodies() throws Exception {
        mvc.perform(post("/api/v1/csp-reports").with(from(ip())).contentType("application/json").content("not json"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/csp-reports").with(from(ip())).contentType("application/json")
                        .content("{\"csp-report\":{\"blocked-uri\":\"" + "x".repeat(9 * 1024) + "\"}}"))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void floodsAreRateLimited() throws Exception {
        String ip = ip();
        for (int i = 0; i < 30; i++) {
            mvc.perform(post("/api/v1/csp-reports").with(from(ip)).contentType("application/csp-report").content("{}"))
                    .andExpect(status().isNoContent());
        }
        mvc.perform(post("/api/v1/csp-reports").with(from(ip)).contentType("application/csp-report").content("{}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void aBigReportArrayLogsAFewLinesNotHundreds(CapturedOutput output) throws Exception {
        StringBuilder many = new StringBuilder("[");
        for (int i = 0; i < 100; i++) {
            many.append(i == 0 ? "" : ",").append("{\"body\":{\"blockedURL\":\"https://x.example/").append(i)
                    .append("\",\"effectiveDirective\":\"img-src\"}}");
        }
        many.append("]");
        mvc.perform(post("/api/v1/csp-reports").with(from(ip())).contentType("application/reports+json").content(many.toString()))
                .andExpect(status().isNoContent());
        long lines = output.getOut().lines().filter(l -> l.contains("CSP violation:")).count();
        org.assertj.core.api.Assertions.assertThat(lines).isLessThanOrEqualTo(6);
        org.assertj.core.api.Assertions.assertThat(output.getOut()).contains("and 95 more");
    }
}

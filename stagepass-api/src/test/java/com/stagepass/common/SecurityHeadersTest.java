package com.stagepass.common;

import com.stagepass.booking.BookingTestSupport;
import com.stagepass.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityHeadersTest extends PostgresIntegrationTest {

    @Autowired
    BookingTestSupport support;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void apiResponsesCarryHeaders() throws Exception {
        mvc.perform(get("/api/v1/public/categories"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=()"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"));
    }

    @Test
    void hstsOnHttps() throws Exception {
        mvc.perform(get("/api/v1/public/categories").secure(true))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"));
    }

    @Test
    void sharePageHasItsOwnCspAndStillWorks() throws Exception {
        var e = support.liveEvent(49900, 20);
        String slug = jdbc.queryForObject("select slug from events where id = ?", String.class, e.eventId());
        MvcResult r = mvc.perform(get("/e/" + slug)).andExpect(status().isOk()).andReturn();
        assertThat(r.getResponse().getHeader("Content-Security-Policy"))
                .isEqualTo("default-src 'none'; img-src https:; style-src 'unsafe-inline'; frame-ancestors 'none'");
        String html = r.getResponse().getContentAsString();
        assertThat(html).contains("og:title").contains("http-equiv=\"refresh\"")
                .contains("http://localhost:5173/events/" + slug).doesNotContain("<script");
    }

    @Test
    void unknownOriginPreflightIs403() throws Exception {
        mvc.perform(options("/api/v1/me").header("Origin", "https://evil.example").header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
        mvc.perform(options("/api/v1/me").header("Origin", "http://localhost:5173").header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void browsersMayReadRetryAfter() throws Exception {
        mvc.perform(get("/api/v1/public/categories").header("Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Expose-Headers", org.hamcrest.Matchers.containsString("Retry-After")));
    }

    @Test
    void prodTrustsOnlyTheRightmostForwardedAddress() throws Exception {
        String prod = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/application-prod.properties"));
        assertThat(prod).contains("server.forward-headers-strategy=native").doesNotContain("forward-headers-strategy=framework");
    }
}

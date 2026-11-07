package com.stagepass.common;

import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@TestPropertySource(properties = "stagepass.rate-limits.enabled=true")
class RateLimitTest extends PostgresIntegrationTest {

    @Autowired
    TestUsers users;

    private static RequestPostProcessor from(String ip) {
        return r -> {
            r.setRemoteAddr(ip);
            return r;
        };
    }

    private static String ip() {
        UUID u = UUID.randomUUID();
        return "10." + (u.getMostSignificantBits() & 0xff) + "." + (u.getLeastSignificantBits() & 0xff) + "." + (u.hashCode() & 0xff);
    }

    private int booking(TestUser u, String ip) throws Exception {
        return mvc.perform(post("/api/v1/bookings").with(from(ip)).header("Authorization", u.bearer())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus();
    }

    @Test
    void eleventhBookingInAMinuteIs429WithRetryAfter() throws Exception {
        TestUser u = users.attendee();
        String ip = ip();
        for (int i = 0; i < 10; i++) {
            assertThat(booking(u, ip)).isNotEqualTo(429);
        }
        MvcResult r = mvc.perform(post("/api/v1/bookings").with(from(ip)).header("Authorization", u.bearer())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(429);
        assertThat(r.getResponse().getContentAsString()).contains("RATE_LIMITED").contains("Too many requests");
        assertThat(Integer.parseInt(r.getResponse().getHeader("Retry-After"))).isGreaterThanOrEqualTo(1);
    }

    @Test
    void usersOnOneIpHaveSeparateLimits() throws Exception {
        String ip = ip();
        TestUser a = users.attendee();
        TestUser b = users.attendee();
        for (int i = 0; i < 10; i++) {
            booking(a, ip);
        }
        assertThat(booking(a, ip)).isEqualTo(429);
        assertThat(booking(b, ip)).isNotEqualTo(429);
    }

    @Test
    void publicGetsAreLimitedByIp() throws Exception {
        String ip = ip();
        int last = 0;
        for (int i = 0; i < 601; i++) {
            last = mvc.perform(get("/api/v1/public/categories").with(from(ip))).andReturn().getResponse().getStatus();
        }
        assertThat(last).isEqualTo(429);
        assertThat(mvc.perform(get("/api/v1/public/categories").with(from(ip()))).andReturn().getResponse().getStatus())
                .isEqualTo(200);
    }

    @Test
    void webhooksHealthAndAdminAreNeverLimited() throws Exception {
        String ip = ip();
        for (int i = 0; i < 200; i++) {
            assertThat(mvc.perform(get("/actuator/health").with(from(ip))).andReturn().getResponse().getStatus()).isEqualTo(200);
        }
        for (int i = 0; i < 30; i++) {
            assertThat(mvc.perform(post("/api/v1/webhooks/razorpay").with(from(ip)).contentType(MediaType.APPLICATION_JSON)
                    .header("X-Razorpay-Signature", "bad").content("{}")).andReturn().getResponse().getStatus()).isNotEqualTo(429);
        }
        TestUser admin = users.admin();
        for (int i = 0; i < 130; i++) {
            assertThat(mvc.perform(get("/api/v1/admin/users").with(from(ip)).header("Authorization", admin.bearer()))
                    .andReturn().getResponse().getStatus()).isEqualTo(200);
        }
    }

    @Test
    void scanAllowsABusyDoor() throws Exception {
        TestUser door = users.attendee();
        String ip = ip();
        String body = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"code\":\"SP-XXXX\"}";
        for (int i = 0; i < 120; i++) {
            assertThat(mvc.perform(post("/api/v1/checkin/scan").with(from(ip)).header("Authorization", door.bearer())
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus()).isNotEqualTo(429);
        }
        assertThat(mvc.perform(post("/api/v1/checkin/scan").with(from(ip)).header("Authorization", door.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus()).isEqualTo(429);
    }

    @Test
    void signedInBrowsingIsCountedPerUserNotPerIp() throws Exception {
        String ip = ip();
        for (int i = 0; i < 130; i++) {
            mvc.perform(get("/api/v1/public/categories").with(from(ip)).header("Authorization", users.attendee().bearer()));
        }
        assertThat(mvc.perform(get("/api/v1/public/categories").with(from(ip))).andReturn().getResponse().getStatus())
                .as("anonymous visitor on the same IP").isEqualTo(200);
    }

    @Test
    void anonymousIpAllowsACampusCrowd() throws Exception {
        String ip = ip();
        int last = 0;
        for (int i = 0; i < 600; i++) {
            last = mvc.perform(get("/api/v1/public/categories").with(from(ip))).andReturn().getResponse().getStatus();
        }
        assertThat(last).isEqualTo(200);
        assertThat(mvc.perform(get("/api/v1/public/categories").with(from(ip))).andReturn().getResponse().getStatus()).isEqualTo(429);
    }
}

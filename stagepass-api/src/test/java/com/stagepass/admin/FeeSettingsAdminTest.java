package com.stagepass.admin;

import com.stagepass.booking.BookingTestSupport;
import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.booking.api.BookingResponse;
import com.stagepass.booking.service.FeeSettingsRepository;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeeSettingsAdminTest extends PostgresIntegrationTest {

    static final String DEFAULTS = "{\"convenienceFeeBps\":250,\"convenienceFeeFlatPaise\":1000,\"gstBps\":1800,"
            + "\"commissionBps\":0,\"refundFeeOnAttendeeRequest\":false}";

    @Autowired
    TestUsers users;

    @Autowired
    BookingTestSupport support;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FeeSettingsRepository fees;

    /** Other tests assume the seeded fees. */
    @AfterEach
    void restoreDefaults() {
        jdbc.update("update platform_settings set value = ?::jsonb where key = 'fees'", DEFAULTS);
        fees.evict();
    }

    private ResultActions putFees(TestUser admin, String json) throws Exception {
        return mvc.perform(put("/api/v1/admin/settings/fees").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long quotedFee(LiveEvent e) throws Exception {
        String json = mvc.perform(post("/api/v1/public/quote").contentType(MediaType.APPLICATION_JSON)
                        .content(BookingTestSupport.items(e.eventId(), e.ticketTypeId(), 1)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(json, "$.price.feePaise")).longValue();
    }

    @Test
    void updateAppliesToNextQuoteImmediately() throws Exception {
        TestUser admin = users.admin();
        LiveEvent e = support.liveEvent(49900, 20);
        BookingResponse existing = support.confirmed(e, 1, users.attendee());
        mvc.perform(get("/api/v1/admin/settings/fees").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.convenienceFeeBps").value(250));
        assertThat(quotedFee(e)).isEqualTo(2248L);

        putFees(admin, DEFAULTS.replace("\"convenienceFeeBps\":250", "\"convenienceFeeBps\":500"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.convenienceFeeBps").value(500));

        assertThat(quotedFee(e)).isEqualTo(3495L);
        assertThat(jdbc.queryForObject("select total_paise from bookings where id = ?", Long.class, existing.id()))
                .isEqualTo(existing.price().totalPaise());
        String audit = jdbc.queryForObject("select before::text || after::text from audit_logs where action = 'SETTINGS_UPDATED'"
                + " and actor_id = ?", String.class, admin.id());
        assertThat(audit).contains("250").contains("500");
        assertThat(jdbc.queryForObject("select updated_by from platform_settings where key = 'fees'", java.util.UUID.class))
                .isEqualTo(admin.id());
    }

    @Test
    void boundsValidated() throws Exception {
        TestUser admin = users.admin();
        putFees(admin, "{\"convenienceFeeBps\":2001,\"convenienceFeeFlatPaise\":100001,\"gstBps\":-1,\"commissionBps\":5001,"
                + "\"refundFeeOnAttendeeRequest\":false}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field",
                        hasItems("convenienceFeeBps", "convenienceFeeFlatPaise", "gstBps", "commissionBps")));
        putFees(admin, "{}").andExpect(status().isBadRequest());
        putFees(users.approvedOrganizer(), DEFAULTS).andExpect(status().isForbidden());
    }
}

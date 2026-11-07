package com.stagepass.config;

import com.stagepass.StagepassApiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** In prod a missing required variable stops startup with a message naming it. */
class ProdStartupTest {

    @Test
    void prodProfileFailsFastWithoutDatabaseUrl() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(StagepassApiApplication.class)
                .profiles("prod")
                .properties("QR_SECRET=test-qr-secret-test-qr-secret-0000",
                        "PAYOUT_ENCRYPTION_KEY=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                        "SUPABASE_URL=https://test.supabase.co", "WEB_BASE_URL=https://web.test",
                        "DB_USERNAME=u", "DB_PASSWORD=p", "stagepass.scheduling.enabled=false",
                        "spring.main.web-application-type=none")
                .run())
                .hasStackTraceContaining("Missing required environment variables: DB_URL");
    }
}

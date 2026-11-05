package com.stagepass.common.config;

import com.stagepass.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** Render tells the service which port to bind through the PORT variable. */
@TestPropertySource(properties = "PORT=10123")
class ServerPortTest extends PostgresIntegrationTest {

    @Autowired
    Environment environment;

    @Test
    void bindsToRenderPort() {
        assertThat(environment.getProperty("server.port")).isEqualTo("10123");
    }
}

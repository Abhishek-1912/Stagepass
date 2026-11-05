package com.stagepass.identity;

import com.stagepass.identity.service.UserProvisioningService;
import com.stagepass.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class UserProvisioningServiceTest extends PostgresIntegrationTest {

    @Autowired
    UserProvisioningService provisioning;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void newUserGetsOneWelcomeEmailQueued() {
        UUID id = UUID.randomUUID();
        String email = "welcome-" + id + "@test.in";

        provisioning.provision(id, email, "Meera", null);
        provisioning.provision(id, email, "Meera", null);

        assertThat(jdbc.queryForObject(
                "select count(*) from email_outbox where to_address = ? and template = 'WELCOME'", Integer.class, email))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select payload->>'name' from email_outbox where to_address = ?", String.class, email))
                .isEqualTo("Meera");
    }

    @Test
    void userWithoutEmailGetsNoWelcomeEmail() {
        UUID id = UUID.randomUUID();
        int before = jdbc.queryForObject("select count(*) from email_outbox", Integer.class);

        provisioning.provision(id, null, null, null);

        assertThat(jdbc.queryForObject("select count(*) from email_outbox", Integer.class)).isEqualTo(before);
    }

    @Test
    void concurrentFirstRequestsCreateOneUser() throws Exception {
        UUID id = UUID.randomUUID();
        String email = "race-" + id + "@test.in";
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return provisioning.provision(id, email, "Racer", null);
            }));
        }
        start.countDown();
        for (Future<?> f : results) {
            f.get(); // rethrows any failure
        }
        pool.shutdown();

        assertThat(jdbc.queryForObject("select count(*) from users where id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from user_roles where user_id = ?", Integer.class, id))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from email_outbox where to_address = ?", Integer.class, email))
                .isEqualTo(1);
    }
}

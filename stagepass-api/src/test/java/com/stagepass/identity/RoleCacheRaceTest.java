package com.stagepass.identity;

import com.stagepass.identity.domain.RoleName;
import com.stagepass.identity.domain.UserStatus;
import com.stagepass.identity.service.RoleLookupService;
import com.stagepass.identity.service.RoleLookupService.AccountSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** A load that read the old state before a suspension committed must not stay cached after the eviction. */
class RoleCacheRaceTest {

    @Test
    void suspensionIsNotUndoneByARacingLoad() throws Exception {
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger loads = new AtomicInteger();
        RoleLookupService service = new RoleLookupService(null) {
            @Override
            protected Optional<AccountSnapshot> load(UUID userId) {
                int n = loads.incrementAndGet();
                if (n == 1) {
                    loading.countDown();
                    await(release);
                    return Optional.of(new AccountSnapshot(UserStatus.ACTIVE, Set.of(RoleName.ATTENDEE)));
                }
                return Optional.of(new AccountSnapshot(UserStatus.SUSPENDED, Set.of(RoleName.ATTENDEE)));
            }
        };
        UUID id = UUID.randomUUID();

        Thread request = new Thread(() -> service.snapshot(id));
        request.start();
        assertThat(loading.await(5, TimeUnit.SECONDS)).isTrue();
        Thread suspension = new Thread(() -> service.evict(id));
        suspension.start();
        Thread.sleep(100);
        release.countDown();
        request.join(5000);
        suspension.join(5000);

        assertThat(service.snapshot(id).orElseThrow().status()).isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    void unknownUsersAreNotCached() {
        AtomicInteger loads = new AtomicInteger();
        RoleLookupService service = new RoleLookupService(null) {
            @Override
            protected Optional<AccountSnapshot> load(UUID userId) {
                loads.incrementAndGet();
                return Optional.empty();
            }
        };
        UUID id = UUID.randomUUID();
        assertThat(service.snapshot(id)).isEmpty();
        assertThat(service.snapshot(id)).isEmpty();
        assertThat(loads.get()).isEqualTo(2);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

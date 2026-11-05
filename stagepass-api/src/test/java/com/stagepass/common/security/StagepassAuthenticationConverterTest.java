package com.stagepass.common.security;

import com.stagepass.common.error.ErrorCode;
import com.stagepass.identity.service.RoleLookupService;
import com.stagepass.identity.service.UserProvisioningService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StagepassAuthenticationConverterTest {

    @Test
    void databaseOutageIsServiceUnavailableNotUnauthenticated() {
        RoleLookupService roles = mock(RoleLookupService.class);
        when(roles.snapshot(any())).thenThrow(new DataAccessResourceFailureException("connection refused"));
        var converter = new StagepassAuthenticationConverter(mock(UserProvisioningService.class), roles);
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "ES256").subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(StagepassAuthenticationException.class)
                .satisfies(e -> assertThat(((StagepassAuthenticationException) e).code())
                        .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
    }
}

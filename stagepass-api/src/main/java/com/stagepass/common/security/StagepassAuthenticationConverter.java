package com.stagepass.common.security;

import com.stagepass.common.error.ApiException;
import com.stagepass.common.error.ErrorCode;
import com.stagepass.identity.domain.UserStatus;
import com.stagepass.identity.service.RoleLookupService;
import com.stagepass.identity.service.RoleLookupService.AccountSnapshot;
import com.stagepass.identity.service.UserProvisioningService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns a validated Supabase JWT into a {@link StagepassAuthenticationToken}. Roles are read from our database,
 * never from token claims. First-time users are provisioned here.
 */
@Component
public class StagepassAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserProvisioningService provisioning;
    private final RoleLookupService roleLookup;

    public StagepassAuthenticationConverter(UserProvisioningService provisioning, RoleLookupService roleLookup) {
        this.provisioning = provisioning;
        this.roleLookup = roleLookup;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId = parseSubject(jwt);
        String email = jwt.getClaimAsString("email");
        AccountSnapshot account = roleLookup.snapshot(userId).orElseGet(() -> provision(userId, email, jwt));

        if (account.status() == UserStatus.SUSPENDED) {
            throw new StagepassAuthenticationException(ErrorCode.ACCOUNT_SUSPENDED,
                    "This account has been suspended. Contact support for help.");
        }
        List<SimpleGrantedAuthority> authorities = account.roles().stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name()))
                .toList();
        return new StagepassAuthenticationToken(new StagepassPrincipal(userId, email, account.roles()), jwt,
                authorities);
    }

    private AccountSnapshot provision(UUID userId, String email, Jwt jwt) {
        Map<String, Object> metadata = jwt.getClaimAsMap("user_metadata");
        String fullName = metadata == null ? null : asString(metadata.get("full_name"));
        String avatarUrl = metadata == null ? null : asString(metadata.get("avatar_url"));
        try {
            provisioning.provision(userId, email, fullName, avatarUrl);
        } catch (ApiException e) {
            throw new StagepassAuthenticationException(e.code(), e.getMessage());
        }
        return roleLookup.snapshot(userId)
                .orElseThrow(() -> new StagepassAuthenticationException(ErrorCode.UNAUTHENTICATED,
                        "Account could not be loaded"));
    }

    private static UUID parseSubject(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new StagepassAuthenticationException(ErrorCode.UNAUTHENTICATED, "Token subject is not a user id");
        }
    }

    private static String asString(Object value) {
        return value instanceof String s && !s.isBlank() ? s : null;
    }
}

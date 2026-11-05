package com.stagepass.common.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;

/** Authentication whose principal is a {@link StagepassPrincipal} and whose credentials are the raw JWT. */
public class StagepassAuthenticationToken extends AbstractAuthenticationToken {

    private final StagepassPrincipal principal;
    private final Jwt jwt;

    public StagepassAuthenticationToken(StagepassPrincipal principal, Jwt jwt,
            Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.jwt = jwt;
        setAuthenticated(true);
    }

    @Override
    public StagepassPrincipal getPrincipal() {
        return principal;
    }

    @Override
    public Jwt getCredentials() {
        return jwt;
    }

    @Override
    public String getName() {
        return principal.userId().toString();
    }
}

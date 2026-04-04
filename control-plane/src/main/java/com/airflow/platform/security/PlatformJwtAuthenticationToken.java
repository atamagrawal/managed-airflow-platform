package com.airflow.platform.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * Authenticated principal after JWT validation. {@code scopedTenantId} is null for admins (all tenants).
 */
public class PlatformJwtAuthenticationToken extends AbstractAuthenticationToken {

    private final String username;
    private final String scopedTenantId;

    public PlatformJwtAuthenticationToken(
            String username,
            String scopedTenantId,
            Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.username = username;
        this.scopedTenantId = scopedTenantId;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return username;
    }

    public String getScopedTenantId() {
        return scopedTenantId;
    }
}

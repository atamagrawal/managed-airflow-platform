package com.airflow.platform.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    /**
     * Tenant id non-admin users must use for deployments; empty if caller is admin.
     */
    public static Optional<String> getNonAdminTenantScope() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof PlatformJwtAuthenticationToken token) {
            return Optional.ofNullable(token.getScopedTenantId());
        }
        return Optional.empty();
    }

    /**
     * Ensures the given tenant id is visible to the current user (admin: any; user: must match JWT scope).
     */
    public static void assertTenantInScope(String tenantId) {
        if (isAdmin()) {
            return;
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new AccessDeniedException("Not authorized");
        }
        String scope = getNonAdminTenantScope()
                .orElseThrow(() -> new AccessDeniedException("Not authorized"));
        if (!scope.equals(tenantId)) {
            throw new AccessDeniedException("Not authorized");
        }
    }
}

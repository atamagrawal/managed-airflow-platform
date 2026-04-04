package com.airflow.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Read-only view of a configured control-plane user (from {@code platform.security.users}).
 * Passwords are never returned.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAccountResponse {

    private String username;
    private List<String> roles;
    /** Same effective scope as JWT for non-admins; null for administrators. */
    private String tenantScope;
    private boolean admin;
    /**
     * True when this non-admin user inherits {@code platform.security.default-tenant-id-for-users}
     * (no per-user {@code tenant-id} set). Always false for admins.
     */
    private boolean usesPlatformDefaultTenant;
}

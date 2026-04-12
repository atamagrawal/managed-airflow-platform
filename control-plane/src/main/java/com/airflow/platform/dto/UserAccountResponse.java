package com.airflow.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Read-only view of a control-plane user. Passwords are never returned.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAccountResponse {

    public static final String SOURCE_DATABASE = "database";
    public static final String SOURCE_CONFIGURATION = "configuration";

    /** Set when {@link #source} is {@value SOURCE_DATABASE}. */
    private Long id;
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

    /** {@value SOURCE_DATABASE} or {@value SOURCE_CONFIGURATION}. */
    @Builder.Default
    private String source = SOURCE_CONFIGURATION;
}

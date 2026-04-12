package com.airflow.platform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configurable users (admin vs regular) and JWT settings for the control plane UI/API.
 */
@Data
@ConfigurationProperties(prefix = "platform.security")
public class PlatformSecurityProperties {

    /**
     * HMAC signing secret; if shorter than 32 bytes it is hashed to 256 bits for HS256.
     */
    private String jwtSecret = "change-me-to-a-long-random-secret-key!!";

    private long jwtExpirationMs = 86_400_000L;

    /**
     * Home tenant for non-admin users when their account does not set {@link UserAccountProperties#tenantId}.
     * Must match an existing {@code Tenant.tenantId} in the database.
     */
    private String defaultTenantIdForUsers = "local-default";

    private Map<String, UserAccountProperties> users = new LinkedHashMap<>();

    @Data
    public static class UserAccountProperties {
        private String password;
        private List<String> roles = new ArrayList<>(List.of("USER"));
        /**
         * Home tenant id ({@code Tenant.tenantId}) for this account when the user is not a platform admin.
         * If blank, {@link PlatformSecurityProperties#defaultTenantIdForUsers} is used.
         * Ignored for JWT scoping when the user has role ADMIN (admins are not tenant-scoped).
         */
        private String tenantId;
    }
}

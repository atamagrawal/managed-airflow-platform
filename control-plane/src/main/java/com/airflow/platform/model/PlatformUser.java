package com.airflow.platform.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Control-plane user stored in the database (admins can add accounts via API).
 * YAML-defined users remain supported as a fallback for bootstrap.
 */
@Entity
@Table(name = "platform_users", uniqueConstraints = @UniqueConstraint(columnNames = "username"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false, length = 200)
    private String passwordHash;

    /**
     * Comma-separated roles, e.g. {@code USER} or {@code ADMIN,USER}.
     */
    @Column(nullable = false, length = 200)
    private String rolesCsv;

    /**
     * Home tenant for non-admins; null means use {@code platform.security.default-tenant-id-for-users}.
     */
    @Column(name = "home_tenant_id", length = 100)
    private String homeTenantId;

    @Column(nullable = false)
    private boolean enabled = true;

    /**
     * AES-GCM encrypted copy of the user's password (keyed from platform JWT secret) so new local deployments can
     * provision matching Airflow FAB users without a Flow Deck login. Updated on each successful DB login.
     */
    @Column(name = "airflow_bootstrap_secret", length = 2048)
    private String airflowBootstrapSecret;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

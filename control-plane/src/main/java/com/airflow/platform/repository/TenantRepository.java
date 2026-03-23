package com.airflow.platform.repository;

import com.airflow.platform.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Tenant entity
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByTenantId(String tenantId);

    Optional<Tenant> findByEmail(String email);

    boolean existsByTenantId(String tenantId);

    boolean existsByEmail(String email);
}

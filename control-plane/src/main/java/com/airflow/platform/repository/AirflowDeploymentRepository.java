package com.airflow.platform.repository;

import com.airflow.platform.model.AirflowDeployment;
import com.airflow.platform.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for AirflowDeployment entity
 */
@Repository
public interface AirflowDeploymentRepository extends JpaRepository<AirflowDeployment, Long> {

    Optional<AirflowDeployment> findByDeploymentId(String deploymentId);

    List<AirflowDeployment> findByTenant(Tenant tenant);

    List<AirflowDeployment> findByTenantTenantId(String tenantId);

    boolean existsByDeploymentId(String deploymentId);

    List<AirflowDeployment> findByStatus(AirflowDeployment.DeploymentStatus status);
}

package com.airflow.platform.repository;

import com.airflow.platform.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Project entity
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByProjectId(String projectId);

    boolean existsByProjectId(String projectId);

    List<Project> findByStatus(Project.ProjectStatus status);

    List<Project> findByOwner(String owner);

    List<Project> findByTenant_TenantId(String tenantId);

    boolean existsByTenant_TenantId(String tenantId);
}

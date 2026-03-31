package com.airflow.platform.repository;

import com.airflow.platform.model.AirflowDeployment;
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

    List<Project> findByDeployment(AirflowDeployment deployment);

    List<Project> findByDeploymentDeploymentId(String deploymentId);

    boolean existsByProjectId(String projectId);

    List<Project> findByStatus(Project.ProjectStatus status);

    List<Project> findByDeploymentAndStatus(AirflowDeployment deployment, Project.ProjectStatus status);

    List<Project> findByOwner(String owner);

    Optional<Project> findByDeploymentAndName(AirflowDeployment deployment, String name);
}

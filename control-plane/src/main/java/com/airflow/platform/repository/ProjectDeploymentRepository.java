package com.airflow.platform.repository;

import com.airflow.platform.model.ProjectDeployment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectDeploymentRepository extends JpaRepository<ProjectDeployment, Long> {

    List<ProjectDeployment> findByProject_ProjectId(String projectId);

    List<ProjectDeployment> findByDeployment_DeploymentId(String deploymentId);

    Optional<ProjectDeployment> findByProject_ProjectIdAndDeployment_DeploymentId(String projectId, String deploymentId);

    boolean existsByProject_ProjectIdAndDeployment_DeploymentId(String projectId, String deploymentId);

    void deleteByProject_ProjectId(String projectId);

    void deleteByProject_ProjectIdAndDeployment_DeploymentId(String projectId, String deploymentId);
}

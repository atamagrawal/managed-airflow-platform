package com.airflow.platform.repository;

import com.airflow.platform.model.AirflowDeployment;
import com.airflow.platform.model.Dag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Dag entity
 */
@Repository
public interface DagRepository extends JpaRepository<Dag, Long> {

    Optional<Dag> findByDagId(String dagId);

    List<Dag> findByDeployment(AirflowDeployment deployment);

    List<Dag> findByDeploymentDeploymentId(String deploymentId);

    boolean existsByDagId(String dagId);

    List<Dag> findByStatus(Dag.DagStatus status);

    List<Dag> findByDeploymentAndStatus(AirflowDeployment deployment, Dag.DagStatus status);

    List<Dag> findByOwner(String owner);

    Optional<Dag> findByDeploymentAndFileName(AirflowDeployment deployment, String fileName);
}

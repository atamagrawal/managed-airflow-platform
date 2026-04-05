package com.airflow.platform.repository;

import com.airflow.platform.model.CachedDagRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface CachedDagRunRepository extends JpaRepository<CachedDagRun, Long> {

    void deleteByDeploymentId(String deploymentId);

    Page<CachedDagRun> findAllByDeploymentIdIn(Collection<String> deploymentIds, Pageable pageable);

    Page<CachedDagRun> findAllByDeploymentId(String deploymentId, Pageable pageable);

    Page<CachedDagRun> findAllByDeploymentIdAndDagId(String deploymentId, String dagId, Pageable pageable);

    Page<CachedDagRun> findAllByDeploymentIdInAndDagId(
            Collection<String> deploymentIds, String dagId, Pageable pageable);
}

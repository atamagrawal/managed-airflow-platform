package com.airflow.platform.repository;

import com.airflow.platform.model.CachedDagMeta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CachedDagMetaRepository extends JpaRepository<CachedDagMeta, Long> {

    void deleteByDeploymentId(String deploymentId);

    Page<CachedDagMeta> findAllByDeploymentIdIn(Collection<String> deploymentIds, Pageable pageable);

    Page<CachedDagMeta> findAllByDeploymentId(String deploymentId, Pageable pageable);

    Page<CachedDagMeta> findAllByDeploymentIdInAndImportError(
            Collection<String> deploymentIds, boolean importError, Pageable pageable);

    List<CachedDagMeta> findByDeploymentIdOrderByDagIdAsc(String deploymentId);
}

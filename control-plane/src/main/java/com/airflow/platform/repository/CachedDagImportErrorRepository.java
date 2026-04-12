package com.airflow.platform.repository;

import com.airflow.platform.model.CachedDagImportError;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CachedDagImportErrorRepository extends JpaRepository<CachedDagImportError, Long> {

    void deleteByDeploymentId(String deploymentId);

    Page<CachedDagImportError> findAllByDeploymentIdIn(Collection<String> deploymentIds, Pageable pageable);

    List<CachedDagImportError> findByDeploymentIdInOrderByFilenameAsc(List<String> deploymentIds);

    List<CachedDagImportError> findByDeploymentIdOrderByFilenameAsc(String deploymentId);
}

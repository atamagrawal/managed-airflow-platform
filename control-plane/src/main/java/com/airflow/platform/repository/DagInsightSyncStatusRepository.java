package com.airflow.platform.repository;

import com.airflow.platform.model.DagInsightSyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DagInsightSyncStatusRepository extends JpaRepository<DagInsightSyncStatus, String> {
}

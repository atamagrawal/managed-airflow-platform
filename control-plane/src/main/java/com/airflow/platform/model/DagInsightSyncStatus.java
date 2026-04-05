package com.airflow.platform.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Last DAG-insights sync attempt per deployment (for UI and ops).
 */
@Entity
@Table(name = "dag_insight_sync_status")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DagInsightSyncStatus {

    @Id
    @Column(name = "deployment_id", length = 100)
    private String deploymentId;

    private Instant lastSyncStartedAt;

    private Instant lastSyncCompletedAt;

    private Boolean lastSyncSuccess;

    @Column(columnDefinition = "TEXT")
    private String lastErrorMessage;
}

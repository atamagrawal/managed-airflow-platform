package com.airflow.platform.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Control-plane cache of recent DAG runs, synced from each deployment's Airflow REST API.
 */
@Entity
@Table(
        name = "cached_dag_runs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"deployment_id", "dag_id", "dag_run_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CachedDagRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deployment_id", nullable = false, length = 100)
    private String deploymentId;

    @Column(name = "dag_id", nullable = false, length = 250)
    private String dagId;

    @Column(name = "dag_run_id", nullable = false, length = 250)
    private String dagRunId;

    @Column(length = 50)
    private String state;

    private Instant logicalDate;

    private Instant startDate;

    private Instant endDate;

    @Column(length = 50)
    private String runType;

    @Column(nullable = false)
    private Instant syncedAt;
}

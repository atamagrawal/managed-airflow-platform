package com.airflow.platform.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Cached DAG metadata and debug signals (pause/active, file path, import-error linkage).
 */
@Entity
@Table(
        name = "cached_dag_meta",
        uniqueConstraints = @UniqueConstraint(columnNames = {"deployment_id", "dag_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CachedDagMeta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deployment_id", nullable = false, length = 100)
    private String deploymentId;

    @Column(name = "dag_id", nullable = false, length = 250)
    private String dagId;

    @Column(nullable = false)
    private boolean paused;

    private Boolean active;

    @Column(length = 2000)
    private String fileloc;

    @Column(length = 500)
    private String owners;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private boolean importError;

    @Column(columnDefinition = "TEXT")
    private String importErrorStackTrace;

    @Column(nullable = false)
    private Instant syncedAt;
}

package com.airflow.platform.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Raw import errors from Airflow ({@code /api/v1/importErrors}), cached per deployment.
 */
@Entity
@Table(
        name = "cached_dag_import_errors",
        uniqueConstraints = @UniqueConstraint(columnNames = {"deployment_id", "filename"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CachedDagImportError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deployment_id", nullable = false, length = 100)
    private String deploymentId;

    @Column(nullable = false, length = 2000)
    private String filename;

    @Column(columnDefinition = "TEXT")
    private String stackTrace;

    private Instant sourceTimestamp;

    @Column(nullable = false)
    private Instant syncedAt;
}

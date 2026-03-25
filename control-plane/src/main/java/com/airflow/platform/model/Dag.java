package com.airflow.platform.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Entity representing a DAG (Directed Acyclic Graph) in Airflow
 */
@Entity
@Table(name = "dags")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Dag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String dagId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deployment_id", nullable = false)
    private AirflowDeployment deployment;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String dagCode;

    @Column(length = 500)
    private String gitRepository;

    @Column(length = 100)
    private String gitBranch;

    @Column(length = 500)
    private String gitPath;

    @Column(length = 100)
    private String gitCommitHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DagStatus status;

    @Column(length = 100)
    private String fileName; // e.g., "my_dag.py"

    @Column(columnDefinition = "TEXT")
    private String validationErrors;

    @Column
    private Boolean isPaused;

    @Column
    private Boolean isActive;

    @Column(length = 100)
    private String owner;

    @Column(length = 500)
    private String tags; // Comma-separated tags

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime lastSyncedAt;

    private LocalDateTime lastDeployedAt;

    public enum DagStatus {
        DRAFT,          // Created but not deployed
        VALIDATING,     // Being validated
        VALID,          // Validated successfully
        INVALID,        // Validation failed
        DEPLOYING,      // Being deployed to Airflow
        DEPLOYED,       // Successfully deployed and running
        FAILED,         // Deployment failed
        UPDATING,       // Being updated
        DELETING,       // Being deleted
        DELETED         // Deleted
    }
}

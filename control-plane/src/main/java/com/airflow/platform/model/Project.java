package com.airflow.platform.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Entity representing an Astronomer-like Airflow project
 * A project contains multiple DAGs, plugins, includes, tests, and configuration files
 */
@Entity
@Table(name = "projects")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String projectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deployment_id", nullable = true)
    private AirflowDeployment deployment;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status;

    // Configuration files content
    @Column(columnDefinition = "TEXT")
    private String requirementsTxt; // Python dependencies

    @Column(columnDefinition = "TEXT")
    private String packagesTxt; // OS-level packages

    @Column(columnDefinition = "TEXT")
    private String dockerfile; // Custom Dockerfile

    @Column(columnDefinition = "TEXT")
    private String airflowSettingsYaml; // Airflow connections, variables, pools

    @Column(columnDefinition = "TEXT")
    private String airflowIgnore; // Files to ignore

    @Column(columnDefinition = "TEXT")
    private String envFile; // Environment variables

    // Git integration
    @Column(length = 500)
    private String gitRepository;

    @Column(length = 100)
    private String gitBranch;

    @Column(length = 100)
    private String gitCommitHash;

    // Project metadata
    @Column(length = 100)
    private String airflowVersion;

    @Column(length = 100)
    private String owner;

    @Column(length = 500)
    private String tags; // Comma-separated tags

    @Column
    private Integer dagCount; // Number of DAGs in this project

    @Column
    private Integer pluginCount; // Number of plugins

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime lastSyncedAt;

    private LocalDateTime lastDeployedAt;

    public enum ProjectStatus {
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

package com.airflow.platform.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Entity representing an Airflow deployment for a tenant
 */
@Entity
@Table(name = "airflow_deployments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AirflowDeployment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String deploymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String description;

    /** Optional short label for lists (e.g. Prod, Staging). Distinct from Kubernetes/docker tags. */
    @Column(length = 100)
    private String tag;

    @Column(nullable = false, length = 50)
    private String airflowVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutorType executorType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeploymentStatus status;

    @Column(length = 100)
    private String namespace;

    @Column(length = 200)
    private String helmReleaseName;

    // Resource specifications
    @Column
    private Integer minWorkers;

    @Column
    private Integer maxWorkers;

    @Column
    private String schedulerCpu;

    @Column
    private String schedulerMemory;

    @Column
    private String workerCpu;

    @Column
    private String workerMemory;

    @Column
    private String webserverCpu;

    @Column
    private String webserverMemory;

    @Column(length = 500)
    private String webserverUrl;

    @Column(length = 500)
    private String ingressHost;

    @Column(columnDefinition = "TEXT")
    private String customConfig; // JSON or YAML for additional configurations

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime deployedAt;

    /** Last user activity for local Docker stacks (deploy, trigger, open Airflow UI, start). Used for idle auto-stop. */
    private LocalDateTime localStackLastActivityAt;

    public enum ExecutorType {
        LOCAL,
        CELERY,
        KUBERNETES,
        CELERY_KUBERNETES
    }

    public enum DeploymentStatus {
        PENDING,
        DEPLOYING,
        RUNNING,
        UPDATING,
        FAILED,
        STOPPED,
        DELETED
    }
}

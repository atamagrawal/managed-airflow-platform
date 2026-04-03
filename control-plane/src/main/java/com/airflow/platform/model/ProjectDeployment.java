package com.airflow.platform.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Links a {@link Project} to an {@link AirflowDeployment}. A project may be deployed to many deployments.
 */
@Entity
@Table(
        name = "project_deployments",
        uniqueConstraints = @UniqueConstraint(name = "uk_project_deployment", columnNames = {"project_id", "deployment_id"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDeployment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deployment_id", nullable = false)
    private AirflowDeployment deployment;

    /** Last successful deploy of this project to this deployment (used for trigger eligibility). */
    @Column
    private LocalDateTime lastDeployedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}

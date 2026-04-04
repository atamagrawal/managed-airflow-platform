package com.airflow.platform.dto;

import com.airflow.platform.model.AirflowDeployment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for Airflow deployment response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentResponse {

    private Long id;
    private String deploymentId;
    private String tenantId;
    private String name;
    private String description;
    private String airflowVersion;
    private String executorType;
    private String status;
    private String namespace;
    private String helmReleaseName;
    private Integer minWorkers;
    private Integer maxWorkers;
    private String schedulerCpu;
    private String schedulerMemory;
    private String workerCpu;
    private String workerMemory;
    private String webserverCpu;
    private String webserverMemory;
    private String webserverUrl;
    private String ingressHost;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deployedAt;
    private LocalDateTime localStackLastActivityAt;

    public static DeploymentResponse fromEntity(AirflowDeployment deployment) {
        return DeploymentResponse.builder()
                .id(deployment.getId())
                .deploymentId(deployment.getDeploymentId())
                .tenantId(deployment.getTenant().getTenantId())
                .name(deployment.getName())
                .description(deployment.getDescription())
                .airflowVersion(deployment.getAirflowVersion())
                .executorType(deployment.getExecutorType().name())
                .status(deployment.getStatus().name())
                .namespace(deployment.getNamespace())
                .helmReleaseName(deployment.getHelmReleaseName())
                .minWorkers(deployment.getMinWorkers())
                .maxWorkers(deployment.getMaxWorkers())
                .schedulerCpu(deployment.getSchedulerCpu())
                .schedulerMemory(deployment.getSchedulerMemory())
                .workerCpu(deployment.getWorkerCpu())
                .workerMemory(deployment.getWorkerMemory())
                .webserverCpu(deployment.getWebserverCpu())
                .webserverMemory(deployment.getWebserverMemory())
                .webserverUrl(deployment.getWebserverUrl())
                .ingressHost(deployment.getIngressHost())
                .createdAt(deployment.getCreatedAt())
                .updatedAt(deployment.getUpdatedAt())
                .deployedAt(deployment.getDeployedAt())
                .localStackLastActivityAt(deployment.getLocalStackLastActivityAt())
                .build();
    }
}

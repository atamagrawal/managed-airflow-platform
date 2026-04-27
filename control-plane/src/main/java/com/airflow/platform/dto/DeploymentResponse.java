package com.airflow.platform.dto;

import com.airflow.platform.model.AirflowDeployment;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO for Airflow deployment response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentResponse {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<DeploymentCreateRequest.WorkerQueueConfig>> WORKER_QUEUE_LIST_TYPE =
            new TypeReference<>() {};

    private Long id;
    private String deploymentId;
    private String tenantId;
    private String name;
    private String description;
    private String tag;
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
    private List<DeploymentCreateRequest.WorkerQueueConfig> workerQueues;
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
                .tag(deployment.getTag())
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
                .workerQueues(readWorkerQueues(deployment.getWorkerQueues()))
                .createdAt(deployment.getCreatedAt())
                .updatedAt(deployment.getUpdatedAt())
                .deployedAt(deployment.getDeployedAt())
                .localStackLastActivityAt(deployment.getLocalStackLastActivityAt())
                .build();
    }

    private static List<DeploymentCreateRequest.WorkerQueueConfig> readWorkerQueues(String rawWorkerQueues) {
        if (rawWorkerQueues == null || rawWorkerQueues.isBlank()) {
            return List.of();
        }
        try {
            List<DeploymentCreateRequest.WorkerQueueConfig> parsed =
                    OBJECT_MAPPER.readValue(rawWorkerQueues, WORKER_QUEUE_LIST_TYPE);
            if (parsed == null || parsed.isEmpty()) {
                return List.of();
            }
            List<DeploymentCreateRequest.WorkerQueueConfig> normalized = new ArrayList<>();
            for (DeploymentCreateRequest.WorkerQueueConfig queue : parsed) {
                if (queue == null || queue.getName() == null || queue.getName().isBlank()) {
                    continue;
                }
                DeploymentCreateRequest.WorkerQueueConfig cleaned = new DeploymentCreateRequest.WorkerQueueConfig();
                cleaned.setName(queue.getName().trim());
                Integer workers = queue.getWorkers();
                cleaned.setWorkers(workers == null || workers < 1 ? 1 : workers);
                normalized.add(cleaned);
            }
            return normalized;
        } catch (Exception e) {
            return List.of();
        }
    }
}

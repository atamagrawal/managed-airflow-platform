package com.airflow.platform.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for creating a new Airflow deployment
 */
@Data
public class DeploymentCreateRequest {

    @NotBlank(message = "Tenant ID is required")
    private String tenantId;

    @NotBlank(message = "Deployment name is required")
    @Size(max = 200, message = "Name must not exceed 200 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @Size(max = 100, message = "Tag must not exceed 100 characters")
    private String tag;

    @NotBlank(message = "Airflow version is required")
    private String airflowVersion;

    @NotBlank(message = "Executor type is required")
    private String executorType; // LOCAL, CELERY, KUBERNETES, CELERY_KUBERNETES

    @Min(value = 1, message = "Minimum workers must be at least 1")
    private Integer minWorkers = 1;

    @Min(value = 1, message = "Maximum workers must be at least 1")
    private Integer maxWorkers = 5;

    private String schedulerCpu = "1000m";
    private String schedulerMemory = "2Gi";
    private String workerCpu = "1000m";
    private String workerMemory = "2Gi";
    private String webserverCpu = "500m";
    private String webserverMemory = "1Gi";

    private String ingressHost;
    private String customConfig;
    @Valid
    @Size(max = 20, message = "Worker queues must not exceed 20 entries")
    private List<WorkerQueueConfig> workerQueues = new ArrayList<>();

    /**
     * When {@code true} and {@code deployment.provider} is {@code local}, only writes compose artifacts — no
     * {@code docker compose up}. When {@code null}, {@code local.auto-start-docker-on-create} applies.
     */
    private Boolean deferDockerStart;

    @Data
    public static class WorkerQueueConfig {
        @NotBlank(message = "Worker queue name is required")
        @Size(max = 100, message = "Worker queue name must not exceed 100 characters")
        private String name;

        @Min(value = 1, message = "Worker queue worker count must be at least 1")
        private Integer workers = 1;
    }
}

package com.airflow.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for creating a new DAG
 * Note: deploymentId is optional for DRAFT status DAGs
 */
@Data
public class DagCreateRequest {

    // Optional - can be null for DRAFT DAGs, required for deploying
    private String deploymentId;

    @NotBlank(message = "DAG name is required")
    @Size(max = 200, message = "Name must not exceed 200 characters")
    private String name;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @NotBlank(message = "DAG code is required")
    private String dagCode;

    @Size(max = 500, message = "Git repository must not exceed 500 characters")
    private String gitRepository;

    @Size(max = 100, message = "Git branch must not exceed 100 characters")
    private String gitBranch;

    @Size(max = 500, message = "Git path must not exceed 500 characters")
    private String gitPath;

    @NotBlank(message = "File name is required")
    @Size(max = 100, message = "File name must not exceed 100 characters")
    private String fileName;

    private Boolean isPaused = false;

    @Size(max = 100, message = "Owner must not exceed 100 characters")
    private String owner;

    @Size(max = 500, message = "Tags must not exceed 500 characters")
    private String tags;
}

package com.airflow.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for creating a new Airflow project
 */
@Data
public class ProjectCreateRequest {

    private String deploymentId;

    @NotBlank(message = "Project name is required")
    @Size(max = 200, message = "Name must not exceed 200 characters")
    private String name;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    private String requirementsTxt;  // Python dependencies

    private String packagesTxt;  // OS-level packages

    private String dockerfile;  // Custom Dockerfile

    private String airflowSettingsYaml;  // Airflow settings

    private String airflowIgnore;  // Files to ignore

    private String envFile;  // Environment variables

    @Size(max = 500, message = "Git repository must not exceed 500 characters")
    private String gitRepository;

    @Size(max = 100, message = "Git branch must not exceed 100 characters")
    private String gitBranch;

    @Size(max = 100, message = "Airflow version must not exceed 100 characters")
    private String airflowVersion;

    @Size(max = 100, message = "Owner must not exceed 100 characters")
    private String owner;

    @Size(max = 500, message = "Tags must not exceed 500 characters")
    private String tags;
}

package com.airflow.platform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for creating a new tenant
 */
@Data
public class TenantCreateRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 200, message = "Name must not exceed 200 characters")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @Size(max = 500, message = "Organization must not exceed 500 characters")
    private String organization;

    @NotBlank(message = "Cloud provider is required")
    private String cloudProvider; // AWS, GCP, AZURE

    @Size(max = 100, message = "Cluster name must not exceed 100 characters")
    private String clusterName;

    @Size(max = 50, message = "Region must not exceed 50 characters")
    private String region;
}

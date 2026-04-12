package com.airflow.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnvironmentConnectionTargetResult {
    private String deploymentId;
    private String deploymentName;
    private String tenantId;
    private boolean success;
    private String message;
}

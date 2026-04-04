package com.airflow.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One DAG file from a project that has been successfully deployed to a specific Airflow deployment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeployedDagResponse {

    private String deploymentId;
    private String deploymentName;
    private String projectId;
    private String projectName;
    private Long fileId;
    private String filePath;
    private String fileName;
    /** Parsed from DAG Python source; may be null if not detectable. */
    private String airflowDagId;
    /** When this project was last successfully deployed to this deployment. */
    private LocalDateTime lastDeployedAt;
}

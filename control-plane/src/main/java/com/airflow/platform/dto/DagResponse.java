package com.airflow.platform.dto;

import com.airflow.platform.model.Dag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for DAG response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DagResponse {

    private Long id;
    private String dagId;
    private String deploymentId;
    private String deploymentName;
    private String name;
    private String description;
    private String dagCode;
    private String gitRepository;
    private String gitBranch;
    private String gitPath;
    private String gitCommitHash;
    private String status;
    private String fileName;
    private String validationErrors;
    private Boolean isPaused;
    private Boolean isActive;
    private String owner;
    private String tags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastSyncedAt;
    private LocalDateTime lastDeployedAt;

    public static DagResponse fromEntity(Dag dag) {
        return DagResponse.builder()
                .id(dag.getId())
                .dagId(dag.getDagId())
                .deploymentId(dag.getDeployment().getDeploymentId())
                .deploymentName(dag.getDeployment().getName())
                .name(dag.getName())
                .description(dag.getDescription())
                .dagCode(dag.getDagCode())
                .gitRepository(dag.getGitRepository())
                .gitBranch(dag.getGitBranch())
                .gitPath(dag.getGitPath())
                .gitCommitHash(dag.getGitCommitHash())
                .status(dag.getStatus().name())
                .fileName(dag.getFileName())
                .validationErrors(dag.getValidationErrors())
                .isPaused(dag.getIsPaused())
                .isActive(dag.getIsActive())
                .owner(dag.getOwner())
                .tags(dag.getTags())
                .createdAt(dag.getCreatedAt())
                .updatedAt(dag.getUpdatedAt())
                .lastSyncedAt(dag.getLastSyncedAt())
                .lastDeployedAt(dag.getLastDeployedAt())
                .build();
    }
}

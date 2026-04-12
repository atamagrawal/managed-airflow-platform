package com.airflow.platform.dto;

import com.airflow.platform.model.Project;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO for Project responses
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResponse {

    private Long id;
    private String projectId;
    private String tenantId;
    /** @deprecated Prefer {@link #linkedDeploymentIds}. Set to the first linked deployment for backward compatibility. */
    private String deploymentId;
    /** @deprecated Prefer {@link #linkedDeploymentIds}. */
    private String deploymentName;
    /** Deployments this project is linked to (can deploy / trigger per deployment). */
    private List<String> linkedDeploymentIds;
    private String name;
    private String description;
    private String status;
    private String requirementsTxt;
    private String packagesTxt;
    private String dockerfile;
    private String airflowSettingsYaml;
    private String airflowIgnore;
    private String envFile;
    private String gitRepository;
    private String gitBranch;
    private String gitCommitHash;
    private String airflowVersion;
    private String owner;
    private String tags;
    private Integer dagCount;
    private Integer pluginCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastSyncedAt;
    private LocalDateTime lastDeployedAt;

    public static ProjectResponse fromEntity(Project project) {
        return fromEntity(project, List.of());
    }

    public static ProjectResponse fromEntity(Project project, List<String> linkedDeploymentIds) {
        List<String> ids = linkedDeploymentIds != null ? new ArrayList<>(linkedDeploymentIds) : new ArrayList<>();
        String firstId = ids.isEmpty() ? null : ids.get(0);
        return ProjectResponse.builder()
                .id(project.getId())
                .projectId(project.getProjectId())
                .tenantId(project.getTenant() != null ? project.getTenant().getTenantId() : null)
                .linkedDeploymentIds(ids)
                .deploymentId(firstId)
                .deploymentName(null)
                .name(project.getName())
                .description(project.getDescription())
                .status(project.getStatus().name())
                .requirementsTxt(project.getRequirementsTxt())
                .packagesTxt(project.getPackagesTxt())
                .dockerfile(project.getDockerfile())
                .airflowSettingsYaml(project.getAirflowSettingsYaml())
                .airflowIgnore(project.getAirflowIgnore())
                .envFile(project.getEnvFile())
                .gitRepository(project.getGitRepository())
                .gitBranch(project.getGitBranch())
                .gitCommitHash(project.getGitCommitHash())
                .airflowVersion(project.getAirflowVersion())
                .owner(project.getOwner())
                .tags(project.getTags())
                .dagCount(project.getDagCount())
                .pluginCount(project.getPluginCount())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .lastSyncedAt(project.getLastSyncedAt())
                .lastDeployedAt(project.getLastDeployedAt())
                .build();
    }
}

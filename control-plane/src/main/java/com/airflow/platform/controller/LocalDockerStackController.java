package com.airflow.platform.controller;

import com.airflow.platform.dto.DeploymentResponse;
import com.airflow.platform.service.LocalDockerStackLifecycleService;
import com.airflow.platform.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * On-demand Docker Compose lifecycle for the {@code local} deployment provider.
 */
@RestController
@RequestMapping("/api/v1/deployments")
@ConditionalOnBean(LocalDockerStackLifecycleService.class)
@RequiredArgsConstructor
@Tag(name = "Local Docker stack", description = "Start/stop local test clusters (Docker Compose)")
public class LocalDockerStackController {

    private final LocalDockerStackLifecycleService lifecycleService;
    private final ProjectService projectService;

    @PostMapping("/{deploymentId}/local-stack/start")
    @Operation(summary = "Start local Docker Compose Airflow (test cluster)")
    public ResponseEntity<DeploymentResponse> start(
            @PathVariable String deploymentId,
            @RequestParam(required = false) String projectId) {
        if (StringUtils.hasText(projectId)) {
            projectService.materializeLocalDeploymentBuildFromProject(projectId.trim(), deploymentId);
        }
        return ResponseEntity.ok(lifecycleService.startCluster(deploymentId));
    }

    @PostMapping("/{deploymentId}/local-stack/stop")
    @Operation(summary = "Stop local Docker Compose Airflow (test cluster)")
    public ResponseEntity<DeploymentResponse> stop(@PathVariable String deploymentId) {
        return ResponseEntity.ok(lifecycleService.stopCluster(deploymentId));
    }
}

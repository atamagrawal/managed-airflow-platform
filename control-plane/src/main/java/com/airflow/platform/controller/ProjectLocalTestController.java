package com.airflow.platform.controller;

import com.airflow.platform.dto.DeploymentResponse;
import com.airflow.platform.dto.ProjectResponse;
import com.airflow.platform.service.ProjectLocalTestDeploymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/local-test")
@ConditionalOnBean(ProjectLocalTestDeploymentService.class)
@RequiredArgsConstructor
@Tag(name = "Project test environment", description = "Lazy Flow Deck test environment (per tenant)")
public class ProjectLocalTestController {

    private final ProjectLocalTestDeploymentService projectLocalTestDeploymentService;

    @PostMapping("/start")
    @Operation(summary = "Start Flow Deck test environment (create if needed), then deploy this project")
    public ResponseEntity<ProjectResponse> start(@PathVariable String projectId) {
        return ResponseEntity.ok(projectLocalTestDeploymentService.start(projectId));
    }

    @PostMapping("/stop")
    @Operation(summary = "Stop the tenant's Flow Deck test environment")
    public ResponseEntity<DeploymentResponse> stop(@PathVariable String projectId) {
        return ResponseEntity.ok(projectLocalTestDeploymentService.stop(projectId));
    }
}

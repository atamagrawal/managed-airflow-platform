package com.airflow.platform.controller;

import com.airflow.platform.dto.DeployedDagResponse;
import com.airflow.platform.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only view of DAG files that exist in projects and have been deployed to at least one deployment.
 */
@RestController
@RequestMapping("/api/v1/deployed-dags")
@RequiredArgsConstructor
@Tag(name = "Deployed DAGs", description = "DAG files from deployed projects")
public class DeployedDagController {

    private final ProjectService projectService;

    @GetMapping
    @Operation(summary = "List deployed project DAGs, optionally filtered by deployment")
    public ResponseEntity<List<DeployedDagResponse>> listDeployedDags(
            @RequestParam(required = false) String deploymentId) {
        return ResponseEntity.ok(projectService.listDeployedProjectDags(deploymentId));
    }
}

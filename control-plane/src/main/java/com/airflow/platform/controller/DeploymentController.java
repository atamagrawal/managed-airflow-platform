package com.airflow.platform.controller;

import com.airflow.platform.dto.DeploymentCreateRequest;
import com.airflow.platform.dto.DeploymentResponse;
import com.airflow.platform.service.AirflowDeploymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for Airflow deployment management
 */
@RestController
@RequestMapping("/api/v1/deployments")
@RequiredArgsConstructor
@Tag(name = "Deployment Management", description = "APIs for managing Airflow deployments")
public class DeploymentController {

    private final AirflowDeploymentService deploymentService;

    @Value("${deployment.provider:kubernetes}")
    private String deploymentProvider;

    @GetMapping("/config")
    @Operation(summary = "Get deployment provider configuration")
    public ResponseEntity<Map<String, String>> getDeploymentConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("provider", deploymentProvider);
        return ResponseEntity.ok(config);
    }

    @PostMapping
    @Operation(summary = "Create a new Airflow deployment")
    public ResponseEntity<DeploymentResponse> createDeployment(@Valid @RequestBody DeploymentCreateRequest request) {
        DeploymentResponse response = deploymentService.createDeployment(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Get deployments visible to the current user (all for admin, tenant-scoped otherwise)")
    public ResponseEntity<List<DeploymentResponse>> getAllDeployments() {
        List<DeploymentResponse> deployments = deploymentService.getDeploymentsForCurrentUser();
        return ResponseEntity.ok(deployments);
    }

    @GetMapping("/{deploymentId}")
    @Operation(summary = "Get deployment by ID")
    public ResponseEntity<DeploymentResponse> getDeployment(@PathVariable String deploymentId) {
        DeploymentResponse response = deploymentService.getDeployment(deploymentId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tenant/{tenantId}")
    @Operation(summary = "Get all deployments for a tenant")
    public ResponseEntity<List<DeploymentResponse>> getDeploymentsByTenant(@PathVariable String tenantId) {
        List<DeploymentResponse> deployments = deploymentService.getDeploymentsByTenantForCaller(tenantId);
        return ResponseEntity.ok(deployments);
    }

    @PutMapping("/{deploymentId}")
    @Operation(summary = "Update an existing deployment")
    public ResponseEntity<DeploymentResponse> updateDeployment(
            @PathVariable String deploymentId,
            @Valid @RequestBody DeploymentCreateRequest request) {
        DeploymentResponse response = deploymentService.updateDeployment(deploymentId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{deploymentId}")
    @Operation(summary = "Delete a deployment")
    public ResponseEntity<Void> deleteDeployment(@PathVariable String deploymentId) {
        deploymentService.deleteDeployment(deploymentId);
        return ResponseEntity.noContent().build();
    }
}

package com.airflow.platform.controller;

import com.airflow.platform.dto.ProjectCreateRequest;
import com.airflow.platform.dto.ProjectFileRequest;
import com.airflow.platform.dto.ProjectFileUpdateRequest;
import com.airflow.platform.dto.ProjectResponse;
import com.airflow.platform.dto.ProjectUpdateRequest;
import com.airflow.platform.model.ProjectFile;
import com.airflow.platform.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

/**
 * REST controller for Airflow project management
 */
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Project Management", description = "APIs for managing Airflow projects")
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @Operation(summary = "Create a new project")
    public ResponseEntity<ProjectResponse> createProject(@Valid @RequestBody ProjectCreateRequest request) {
        ProjectResponse response = projectService.createProject(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Get all projects")
    public ResponseEntity<List<ProjectResponse>> getAllProjects() {
        List<ProjectResponse> projects = projectService.getAllProjects();
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "Get project by ID")
    public ResponseEntity<ProjectResponse> getProject(@PathVariable String projectId) {
        ProjectResponse response = projectService.getProject(projectId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/deployment/{deploymentId}")
    @Operation(summary = "Get all projects for a deployment")
    public ResponseEntity<List<ProjectResponse>> getProjectsByDeployment(@PathVariable String deploymentId) {
        List<ProjectResponse> projects = projectService.getProjectsByDeployment(deploymentId);
        return ResponseEntity.ok(projects);
    }

    @PutMapping("/{projectId}")
    @Operation(summary = "Update an existing project")
    public ResponseEntity<ProjectResponse> updateProject(
            @PathVariable String projectId,
            @Valid @RequestBody ProjectUpdateRequest request) {
        ProjectResponse response = projectService.updateProject(projectId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{projectId}")
    @Operation(summary = "Delete a project")
    public ResponseEntity<Void> deleteProject(@PathVariable String projectId) {
        projectService.deleteProject(projectId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{projectId}/deployments/{deploymentId}")
    @Operation(summary = "Link a project to a deployment (optional; deploy also creates the link)")
    public ResponseEntity<ProjectResponse> linkProjectDeployment(
            @PathVariable String projectId,
            @PathVariable String deploymentId) {
        return ResponseEntity.ok(projectService.linkProjectToDeployment(projectId, deploymentId));
    }

    @DeleteMapping("/{projectId}/deployments/{deploymentId}")
    @Operation(summary = "Remove link between a project and a deployment")
    public ResponseEntity<Void> unlinkProjectDeployment(
            @PathVariable String projectId,
            @PathVariable String deploymentId) {
        projectService.unlinkProjectFromDeployment(projectId, deploymentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{projectId}/deploy")
    @Operation(summary = "Deploy a project to a specific Airflow deployment")
    public ResponseEntity<ProjectResponse> deployProject(
            @PathVariable String projectId,
            @RequestParam String deploymentId) {
        ProjectResponse response = projectService.deployProject(projectId, deploymentId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{projectId}/trigger")
    @Operation(summary = "Trigger DAG runs for DAG files in a project on a specific deployment")
    public ResponseEntity<Map<String, Object>> triggerProject(
            @PathVariable String projectId,
            @RequestParam String deploymentId,
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) String workerQueue) {
        Map<String, Object> response = projectService.triggerProject(projectId, deploymentId, fileName, workerQueue);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{projectId}/files")
    @Operation(summary = "Add a file to the project")
    public ResponseEntity<Void> addFileToProject(
            @PathVariable String projectId,
            @Valid @RequestBody ProjectFileRequest request) {
        projectService.addFileToProject(projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/{projectId}/files")
    @Operation(summary = "Get all files in a project")
    public ResponseEntity<List<ProjectFile>> getProjectFiles(@PathVariable String projectId) {
        List<ProjectFile> files = projectService.getProjectFiles(projectId);
        return ResponseEntity.ok(files);
    }

    @PutMapping("/{projectId}/files/{fileId}")
    @Operation(summary = "Update a project file (e.g. DAG or plugin source)")
    public ResponseEntity<Void> updateProjectFile(
            @PathVariable String projectId,
            @PathVariable Long fileId,
            @Valid @RequestBody ProjectFileUpdateRequest request) {
        projectService.updateProjectFile(projectId, fileId, request);
        return ResponseEntity.noContent().build();
    }
}

package com.airflow.platform.controller;

import com.airflow.platform.dto.DagCreateRequest;
import com.airflow.platform.dto.DagResponse;
import com.airflow.platform.dto.DagUpdateRequest;
import com.airflow.platform.service.DagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for DAG management
 */
@RestController
@RequestMapping("/api/v1/dags")
@RequiredArgsConstructor
@Tag(name = "DAG Management", description = "APIs for managing Airflow DAGs")
public class DagController {

    private final DagService dagService;

    @PostMapping
    @Operation(summary = "Create a new DAG")
    public ResponseEntity<DagResponse> createDag(@Valid @RequestBody DagCreateRequest request) {
        DagResponse response = dagService.createDag(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Get all DAGs")
    public ResponseEntity<List<DagResponse>> getAllDags() {
        List<DagResponse> dags = dagService.getAllDags();
        return ResponseEntity.ok(dags);
    }

    @GetMapping("/{dagId}")
    @Operation(summary = "Get DAG by ID")
    public ResponseEntity<DagResponse> getDag(@PathVariable String dagId) {
        DagResponse response = dagService.getDag(dagId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/deployment/{deploymentId}")
    @Operation(summary = "Get all DAGs for a deployment")
    public ResponseEntity<List<DagResponse>> getDagsByDeployment(@PathVariable String deploymentId) {
        List<DagResponse> dags = dagService.getDagsByDeployment(deploymentId);
        return ResponseEntity.ok(dags);
    }

    @PutMapping("/{dagId}")
    @Operation(summary = "Update an existing DAG")
    public ResponseEntity<DagResponse> updateDag(
            @PathVariable String dagId,
            @Valid @RequestBody DagUpdateRequest request) {
        DagResponse response = dagService.updateDag(dagId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{dagId}")
    @Operation(summary = "Delete a DAG")
    public ResponseEntity<Void> deleteDag(@PathVariable String dagId) {
        dagService.deleteDag(dagId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{dagId}/deploy")
    @Operation(summary = "Deploy a DAG to Airflow")
    public ResponseEntity<DagResponse> deployDag(@PathVariable String dagId) {
        DagResponse response = dagService.deployDag(dagId);
        return ResponseEntity.ok(response);
    }
}

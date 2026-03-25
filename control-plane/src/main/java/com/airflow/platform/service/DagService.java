package com.airflow.platform.service;

import com.airflow.platform.dto.DagCreateRequest;
import com.airflow.platform.dto.DagResponse;
import com.airflow.platform.dto.DagUpdateRequest;
import com.airflow.platform.exception.DeploymentException;
import com.airflow.platform.exception.ResourceNotFoundException;
import com.airflow.platform.model.AirflowDeployment;
import com.airflow.platform.model.Dag;
import com.airflow.platform.repository.AirflowDeploymentRepository;
import com.airflow.platform.repository.DagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for managing Airflow DAGs
 * Supports DAG creation, validation, and deployment to Airflow environments
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DagService {

    private final DagRepository dagRepository;
    private final AirflowDeploymentRepository deploymentRepository;

    private static final Pattern DAG_ID_PATTERN = Pattern.compile("dag_id\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern DAG_OBJECT_PATTERN = Pattern.compile("\\bDAG\\s*\\(");

    @Transactional
    public DagResponse createDag(DagCreateRequest request) {
        log.info("Creating DAG: {} for deployment: {}", request.getName(), request.getDeploymentId());

        // Get deployment
        AirflowDeployment deployment = deploymentRepository.findByDeploymentId(request.getDeploymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + request.getDeploymentId()));

        // Check if DAG with same filename already exists for this deployment
        if (dagRepository.findByDeploymentAndFileName(deployment, request.getFileName()).isPresent()) {
            throw new DeploymentException("DAG with filename '" + request.getFileName() + "' already exists for this deployment");
        }

        // Generate DAG ID
        String dagId = generateDagId(request.getName());

        // Validate DAG code
        String validationErrors = validateDagCode(request.getDagCode());

        // Create DAG entity
        Dag dag = new Dag();
        dag.setDagId(dagId);
        dag.setDeployment(deployment);
        dag.setName(request.getName());
        dag.setDescription(request.getDescription());
        dag.setDagCode(request.getDagCode());
        dag.setGitRepository(request.getGitRepository());
        dag.setGitBranch(request.getGitBranch());
        dag.setGitPath(request.getGitPath());
        dag.setFileName(request.getFileName());
        dag.setIsPaused(request.getIsPaused());
        dag.setIsActive(true);
        dag.setOwner(request.getOwner());
        dag.setTags(request.getTags());
        dag.setValidationErrors(validationErrors);

        // Set status based on validation
        if (validationErrors == null || validationErrors.isEmpty()) {
            dag.setStatus(Dag.DagStatus.VALID);
        } else {
            dag.setStatus(Dag.DagStatus.INVALID);
        }

        dag = dagRepository.save(dag);
        log.info("DAG created successfully: {}", dagId);

        return DagResponse.fromEntity(dag);
    }

    @Transactional(readOnly = true)
    public DagResponse getDag(String dagId) {
        Dag dag = dagRepository.findByDagId(dagId)
                .orElseThrow(() -> new ResourceNotFoundException("DAG not found: " + dagId));
        return DagResponse.fromEntity(dag);
    }

    @Transactional(readOnly = true)
    public List<DagResponse> getDagsByDeployment(String deploymentId) {
        return dagRepository.findByDeploymentDeploymentId(deploymentId).stream()
                .map(DagResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DagResponse> getAllDags() {
        return dagRepository.findAll().stream()
                .map(DagResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public DagResponse updateDag(String dagId, DagUpdateRequest request) {
        log.info("Updating DAG: {}", dagId);

        Dag dag = dagRepository.findByDagId(dagId)
                .orElseThrow(() -> new ResourceNotFoundException("DAG not found: " + dagId));

        // Update fields if provided
        if (request.getName() != null) {
            dag.setName(request.getName());
        }
        if (request.getDescription() != null) {
            dag.setDescription(request.getDescription());
        }
        if (request.getDagCode() != null) {
            dag.setDagCode(request.getDagCode());

            // Re-validate if code is updated
            String validationErrors = validateDagCode(request.getDagCode());
            dag.setValidationErrors(validationErrors);

            if (validationErrors == null || validationErrors.isEmpty()) {
                dag.setStatus(Dag.DagStatus.VALID);
            } else {
                dag.setStatus(Dag.DagStatus.INVALID);
            }
        }
        if (request.getGitRepository() != null) {
            dag.setGitRepository(request.getGitRepository());
        }
        if (request.getGitBranch() != null) {
            dag.setGitBranch(request.getGitBranch());
        }
        if (request.getGitPath() != null) {
            dag.setGitPath(request.getGitPath());
        }
        if (request.getFileName() != null) {
            // Check if new filename conflicts with existing DAG
            if (!dag.getFileName().equals(request.getFileName()) &&
                    dagRepository.findByDeploymentAndFileName(dag.getDeployment(), request.getFileName()).isPresent()) {
                throw new DeploymentException("DAG with filename '" + request.getFileName() + "' already exists for this deployment");
            }
            dag.setFileName(request.getFileName());
        }
        if (request.getIsPaused() != null) {
            dag.setIsPaused(request.getIsPaused());
        }
        if (request.getOwner() != null) {
            dag.setOwner(request.getOwner());
        }
        if (request.getTags() != null) {
            dag.setTags(request.getTags());
        }

        dag = dagRepository.save(dag);
        log.info("DAG updated successfully: {}", dagId);

        return DagResponse.fromEntity(dag);
    }

    @Transactional
    public void deleteDag(String dagId) {
        log.info("Deleting DAG: {}", dagId);

        Dag dag = dagRepository.findByDagId(dagId)
                .orElseThrow(() -> new ResourceNotFoundException("DAG not found: " + dagId));

        dag.setStatus(Dag.DagStatus.DELETING);
        dagRepository.save(dag);

        try {
            // TODO: Remove DAG from Airflow deployment
            // This will be implemented in the sync mechanism task

            dag.setStatus(Dag.DagStatus.DELETED);
            dag.setIsActive(false);
            dagRepository.save(dag);
            log.info("DAG deleted successfully: {}", dagId);
        } catch (Exception e) {
            log.error("Failed to delete DAG: {}", dagId, e);
            dag.setStatus(Dag.DagStatus.FAILED);
            dagRepository.save(dag);
            throw new DeploymentException("Failed to delete DAG: " + e.getMessage(), e);
        }
    }

    @Transactional
    public DagResponse deployDag(String dagId) {
        log.info("Deploying DAG: {}", dagId);

        Dag dag = dagRepository.findByDagId(dagId)
                .orElseThrow(() -> new ResourceNotFoundException("DAG not found: " + dagId));

        // Validate before deployment
        if (dag.getValidationErrors() != null && !dag.getValidationErrors().isEmpty()) {
            throw new DeploymentException("Cannot deploy DAG with validation errors: " + dag.getValidationErrors());
        }

        dag.setStatus(Dag.DagStatus.DEPLOYING);
        dagRepository.save(dag);

        try {
            // TODO: Deploy DAG to Airflow deployment
            // This will be implemented in the sync mechanism task
            // For now, we'll just mark it as deployed

            dag.setStatus(Dag.DagStatus.DEPLOYED);
            dag.setLastDeployedAt(LocalDateTime.now());
            dag = dagRepository.save(dag);
            log.info("DAG deployed successfully: {}", dagId);
        } catch (Exception e) {
            log.error("Failed to deploy DAG: {}", dagId, e);
            dag.setStatus(Dag.DagStatus.FAILED);
            dagRepository.save(dag);
            throw new DeploymentException("Failed to deploy DAG: " + e.getMessage(), e);
        }

        return DagResponse.fromEntity(dag);
    }

    /**
     * Validates DAG Python code for basic syntax and structure
     */
    private String validateDagCode(String dagCode) {
        StringBuilder errors = new StringBuilder();

        if (dagCode == null || dagCode.trim().isEmpty()) {
            return "DAG code cannot be empty";
        }

        // Check for basic Airflow imports
        if (!dagCode.contains("from airflow") && !dagCode.contains("import airflow")) {
            errors.append("Missing Airflow imports. ");
        }

        // Check for DAG definition
        if (!DAG_OBJECT_PATTERN.matcher(dagCode).find()) {
            errors.append("No DAG object found. ");
        }

        // Check for basic Python syntax issues
        if (!dagCode.contains("import") && !dagCode.contains("from")) {
            errors.append("No import statements found. ");
        }

        // Check for balanced parentheses
        int parenCount = 0;
        for (char c : dagCode.toCharArray()) {
            if (c == '(') parenCount++;
            if (c == ')') parenCount--;
            if (parenCount < 0) {
                errors.append("Unbalanced parentheses. ");
                break;
            }
        }
        if (parenCount > 0) {
            errors.append("Unclosed parentheses. ");
        }

        return errors.length() > 0 ? errors.toString().trim() : null;
    }

    private String generateDagId(String name) {
        String baseDagId = name.toLowerCase()
                .replaceAll("[^a-z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");

        String dagId = baseDagId + "_" + UUID.randomUUID().toString().substring(0, 8);

        while (dagRepository.existsByDagId(dagId)) {
            dagId = baseDagId + "_" + UUID.randomUUID().toString().substring(0, 8);
        }

        return dagId;
    }
}

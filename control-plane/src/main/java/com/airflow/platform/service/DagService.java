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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for managing Airflow DAGs
 * Supports DAG creation, validation, and deployment to Airflow environments
 */
@Service
@Slf4j
public class DagService {

    private final DagRepository dagRepository;
    private final AirflowDeploymentRepository deploymentRepository;
    private final RestTemplate restTemplate;

    @Value("${deployment.provider:local}")
    private String deploymentProvider;

    @Value("${local.base-directory:${user.home}/airflow-deployments}")
    private String localBaseDirectory;

    /** Matches docker-compose user creation (airflow users create ... --username admin --password admin) */
    @Value("${airflow.api.username:admin}")
    private String airflowApiUsername;

    @Value("${airflow.api.password:admin}")
    private String airflowApiPassword;

    private static final Pattern DAG_OBJECT_PATTERN = Pattern.compile("\\bDAG\\s*\\(");

    public DagService(DagRepository dagRepository,
                      AirflowDeploymentRepository deploymentRepository,
                      RestTemplate restTemplate) {
        this.dagRepository = dagRepository;
        this.deploymentRepository = deploymentRepository;
        this.restTemplate = restTemplate;
    }

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
            // Remove DAG from Airflow deployment
            if ("local".equalsIgnoreCase(deploymentProvider)) {
                deleteDagFromLocal(dag);
            } else if ("kubernetes".equalsIgnoreCase(deploymentProvider)) {
                deleteDagFromKubernetes(dag);
            } else if ("ecs".equalsIgnoreCase(deploymentProvider)) {
                deleteDagFromECS(dag);
            } else if ("ec2".equalsIgnoreCase(deploymentProvider)) {
                deleteDagFromEC2(dag);
            }

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

    /**
     * Delete DAG from local filesystem
     */
    private void deleteDagFromLocal(Dag dag) throws IOException {
        AirflowDeployment deployment = dag.getDeployment();
        String tenantId = deployment.getTenant().getTenantId();
        String deploymentId = deployment.getDeploymentId();

        Path dagFilePath = Paths.get(localBaseDirectory, tenantId, deploymentId, "dags", dag.getFileName());

        if (Files.exists(dagFilePath)) {
            Files.delete(dagFilePath);
            log.info("DAG file deleted from: {}", dagFilePath);
        } else {
            log.warn("DAG file not found at: {}", dagFilePath);
        }
    }

    /**
     * Delete DAG from Kubernetes
     */
    private void deleteDagFromKubernetes(Dag dag) {
        log.warn("Kubernetes DAG deletion not yet implemented. DAG: {}", dag.getDagId());
        // Don't throw exception for deletion - just log warning
    }

    /**
     * Delete DAG from ECS
     */
    private void deleteDagFromECS(Dag dag) {
        log.warn("ECS DAG deletion not yet implemented. DAG: {}", dag.getDagId());
        // Don't throw exception for deletion - just log warning
    }

    /**
     * Delete DAG from EC2
     */
    private void deleteDagFromEC2(Dag dag) {
        log.warn("EC2 DAG deletion not yet implemented. DAG: {}", dag.getDagId());
        // Don't throw exception for deletion - just log warning
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
            // Deploy DAG to Airflow deployment based on provider type
            if ("local".equalsIgnoreCase(deploymentProvider)) {
                deployDagToLocal(dag);
                unpauseDagInAirflowIfNeeded(dag);
            } else if ("kubernetes".equalsIgnoreCase(deploymentProvider)) {
                deployDagToKubernetes(dag);
            } else if ("ecs".equalsIgnoreCase(deploymentProvider)) {
                deployDagToECS(dag);
            } else if ("ec2".equalsIgnoreCase(deploymentProvider)) {
                deployDagToEC2(dag);
            } else {
                throw new DeploymentException("Unsupported deployment provider: " + deploymentProvider);
            }

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
     * Airflow is configured with `DAGS_ARE_PAUSED_AT_CREATION` in the docker compose generator.
     * To honor the user's `isPaused` choice for locally deployed DAGs, we unpause (best-effort)
     * via the Airflow REST API when `isPaused=false`.
     */
    private void unpauseDagInAirflowIfNeeded(Dag dag) {
        if (dag.getIsPaused() == null || dag.getIsPaused()) {
            return; // either unspecified or explicitly paused
        }

        AirflowDeployment deployment = dag.getDeployment();
        if (deployment == null) {
            return;
        }

        String webserverUrl = deployment.getWebserverUrl();
        if (webserverUrl == null || webserverUrl.isEmpty()) {
            log.warn("Skipping DAG unpause: webserverUrl not found for deployment {}", deployment.getDeploymentId());
            return;
        }

        // Use the DAG id defined inside the python code.
        String airflowDagId = extractAirflowDagId(dag.getDagCode());
        if (airflowDagId == null) {
            log.warn("Skipping DAG unpause: could not extract airflow dag_id from dagCode for {}", dag.getDagId());
            return;
        }

        try {
            setAirflowDagPaused(deployment, airflowDagId, false);
        } catch (Exception e) {
            // Deployment succeeded, so don't fail the deploy flow if the API unpause call fails.
            log.warn("Best-effort DAG unpause failed for {}: {}", airflowDagId, e.getMessage());
        }
    }

    private void setAirflowDagPaused(AirflowDeployment deployment, String airflowDagId, boolean isPaused) {
        String baseUrl = normalizeBaseUrl(deployment.getWebserverUrl());
        String encodedDagId = UriUtils.encodePathSegment(airflowDagId, StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        if (isAirflow3OrLater(deployment.getAirflowVersion())) {
            String token = obtainAirflowAccessToken(baseUrl);
            headers.setBearerAuth(token);
            String patchUrl = baseUrl + "/api/v2/dags/" + encodedDagId;

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("is_paused", isPaused);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            restTemplate.exchange(patchUrl, HttpMethod.PATCH, request, Map.class);
        } else {
            headers.setBasicAuth(airflowApiUsername, airflowApiPassword);
            String patchUrl = baseUrl + "/api/v1/dags/" + encodedDagId;

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("is_paused", isPaused);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            restTemplate.exchange(patchUrl, HttpMethod.PATCH, request, Map.class);
        }
    }

    /**
     * Deploy DAG to local filesystem (Docker Compose deployment)
     */
    private void deployDagToLocal(Dag dag) throws IOException {
        AirflowDeployment deployment = dag.getDeployment();
        String tenantId = deployment.getTenant().getTenantId();
        String deploymentId = deployment.getDeploymentId();

        // Construct path: ~/airflow-deployments/{tenant-id}/{deployment-id}/dags/
        Path dagsDirectory = Paths.get(localBaseDirectory, tenantId, deploymentId, "dags");

        // Create directory if it doesn't exist
        if (!Files.exists(dagsDirectory)) {
            Files.createDirectories(dagsDirectory);
            log.info("Created DAGs directory: {}", dagsDirectory);
        }

        // Write DAG file
        Path dagFilePath = dagsDirectory.resolve(dag.getFileName());
        Files.writeString(dagFilePath, dag.getDagCode(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        log.info("DAG file written to: {}", dagFilePath);
    }

    /**
     * Deploy DAG to Kubernetes (ConfigMap or PVC)
     * TODO: Implement Kubernetes deployment
     */
    private void deployDagToKubernetes(Dag dag) {
        log.warn("Kubernetes DAG deployment not yet implemented. DAG: {}", dag.getDagId());
        throw new DeploymentException("Kubernetes DAG deployment is not yet implemented. " +
                "Please manually add the DAG to your Airflow deployment or use Git-sync.");
    }

    /**
     * Deploy DAG to ECS (Write to EFS volume)
     * TODO: Implement ECS deployment
     */
    private void deployDagToECS(Dag dag) {
        log.warn("ECS DAG deployment not yet implemented. DAG: {}", dag.getDagId());
        throw new DeploymentException("ECS DAG deployment is not yet implemented. " +
                "Please manually add the DAG to your Airflow deployment or use Git-sync.");
    }

    /**
     * Deploy DAG to EC2 (Use SSM to write file)
     * TODO: Implement EC2 deployment via SSM
     */
    private void deployDagToEC2(Dag dag) {
        log.warn("EC2 DAG deployment not yet implemented. DAG: {}", dag.getDagId());
        throw new DeploymentException("EC2 DAG deployment is not yet implemented. " +
                "Please manually add the DAG to your Airflow deployment or use Git-sync.");
    }

    /**
     * Trigger a DAG run in Airflow
     */
    @Transactional(readOnly = true)
    public Map<String, Object> triggerDagRun(String dagId) {
        log.info("Triggering DAG run: {}", dagId);

        Dag dag = dagRepository.findByDagId(dagId)
                .orElseThrow(() -> new ResourceNotFoundException("DAG not found: " + dagId));

        // Check if DAG is deployed
        if (dag.getStatus() != Dag.DagStatus.DEPLOYED) {
            throw new DeploymentException("Cannot trigger DAG run. DAG must be deployed first. Current status: " + dag.getStatus());
        }

        AirflowDeployment deployment = dag.getDeployment();
        String webserverUrl = deployment.getWebserverUrl();

        if (webserverUrl == null || webserverUrl.isEmpty()) {
            throw new DeploymentException("Airflow webserver URL not found for deployment: " + deployment.getDeploymentId());
        }

        // Extract the actual DAG ID from the Python code
        String airflowDagId = extractAirflowDagId(dag.getDagCode());
        if (airflowDagId == null) {
            throw new DeploymentException("Could not extract DAG ID from DAG code. Make sure the DAG has a dag_id parameter.");
        }

        try {
            String baseUrl = normalizeBaseUrl(webserverUrl);
            ResponseEntity<Map> response;
            if (isAirflow3OrLater(deployment.getAirflowVersion())) {
                String token = obtainAirflowAccessToken(baseUrl);
                response = postTriggerDagRunAirflow3(baseUrl, airflowDagId, token);
            } else {
                response = postTriggerDagRunAirflow2(baseUrl, airflowDagId);
            }

            log.info("DAG run triggered successfully: {}", airflowDagId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "DAG run triggered successfully");
            result.put("airflowDagId", airflowDagId);
            result.put("response", response.getBody());

            return result;

        } catch (Exception e) {
            log.error("Failed to trigger DAG run: {}", dagId, e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Failed to trigger DAG run: " + e.getMessage());
            result.put("error", e.getMessage());
            return result;
        }
    }

    private static String normalizeBaseUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Airflow 3+ uses stable REST API under /api/v2 and JWT from POST /auth/token.
     */
    private boolean isAirflow3OrLater(String airflowVersion) {
        if (airflowVersion == null || airflowVersion.isBlank()) {
            return true;
        }
        int end = 0;
        while (end < airflowVersion.length() && Character.isDigit(airflowVersion.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return true;
        }
        try {
            return Integer.parseInt(airflowVersion.substring(0, end)) >= 3;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private String obtainAirflowAccessToken(String baseUrl) {
        String tokenUrl = baseUrl + "/auth/token";
        Map<String, String> body = new HashMap<>();
        body.put("username", airflowApiUsername);
        body.put("password", airflowApiPassword);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
        Map<?, ?> respBody = response.getBody();
        if (respBody == null || respBody.get("access_token") == null) {
            throw new DeploymentException("Airflow auth response missing access_token");
        }
        return respBody.get("access_token").toString();
    }

    private ResponseEntity<Map> postTriggerDagRunAirflow3(String baseUrl, String airflowDagId, String accessToken) {
        String encodedDagId = UriUtils.encodePathSegment(airflowDagId, StandardCharsets.UTF_8);
        String triggerUrl = baseUrl + "/api/v2/dags/" + encodedDagId + "/dagRuns";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("conf", new HashMap<>());
        // Airflow 3 stable API requires `logical_date` for creating a DAG run.
        // Use current time in UTC; Airflow will treat it as the run's logical date.
        requestBody.put("logical_date", DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC)));
        requestBody.put("dag_run_id", "manual_" + System.currentTimeMillis());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        return restTemplate.exchange(triggerUrl, HttpMethod.POST, request, Map.class);
    }

    private ResponseEntity<Map> postTriggerDagRunAirflow2(String baseUrl, String airflowDagId) {
        String encodedDagId = UriUtils.encodePathSegment(airflowDagId, StandardCharsets.UTF_8);
        String triggerUrl = baseUrl + "/api/v1/dags/" + encodedDagId + "/dagRuns";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("conf", new HashMap<>());
        requestBody.put("dag_run_id", "manual_" + System.currentTimeMillis());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(airflowApiUsername, airflowApiPassword);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        return restTemplate.exchange(triggerUrl, HttpMethod.POST, request, Map.class);
    }

    /**
     * Extract the Airflow DAG ID from Python code
     * Looks for dag_id parameter in DAG constructor or variable assignment
     */
    private String extractAirflowDagId(String dagCode) {
        // Try to find dag_id in DAG constructor: DAG('dag_id', ...)
        Pattern dagConstructorPattern = Pattern.compile("DAG\\s*\\(\\s*['\"]([^'\"]+)['\"]");
        java.util.regex.Matcher matcher = dagConstructorPattern.matcher(dagCode);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Try to find dag_id parameter: dag_id='my_dag'
        Pattern dagIdPattern = Pattern.compile("dag_id\\s*=\\s*['\"]([^'\"]+)['\"]");
        matcher = dagIdPattern.matcher(dagCode);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
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

package com.airflow.platform.service;

import com.airflow.platform.dto.DeploymentCreateRequest;
import com.airflow.platform.dto.DeploymentResponse;
import com.airflow.platform.dto.ProjectResponse;
import com.airflow.platform.exception.DeploymentException;
import com.airflow.platform.exception.ResourceNotFoundException;
import com.airflow.platform.model.AirflowDeployment;
import com.airflow.platform.repository.AirflowDeploymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Lazily creates a per-tenant deployment for Flow Deck IDE test runs, then materializes the project build,
 * starts the stack, and deploys the project.
 */
@Service
@ConditionalOnBean(LocalDockerStackLifecycleService.class)
@RequiredArgsConstructor
@Slf4j
public class ProjectLocalTestDeploymentService {

    private static final List<String> LEGACY_TEST_DEPLOYMENT_NAMES = List.of("Shared test", "Local test");

    private final ProjectService projectService;
    private final AirflowDeploymentService airflowDeploymentService;
    private final AirflowDeploymentRepository deploymentRepository;
    private final LocalDockerStackLifecycleService localDockerStackLifecycleService;

    @Value("${local.test-deployment.name:Test environment}")
    private String testDeploymentName;

    @Value("${local.test-deployment.description:On-demand test environment for Flow Deck IDE}")
    private String testDeploymentDescription;

    @Value("${local.test-deployment.airflow-version:3.1.8}")
    private String testAirflowVersion;

    @Value("${local.test-deployment.executor-type:LOCAL}")
    private String testExecutorType;

    public ProjectResponse start(String projectId) {
        ProjectResponse overview = projectService.getProject(projectId);
        String tenantId = overview.getTenantId();
        AirflowDeployment deployment = findOrCreateTestDeployment(tenantId);
        String deploymentId = deployment.getDeploymentId();
        log.info("Starting Flow Deck test environment for project {} on deployment {}", projectId, deploymentId);
        projectService.materializeLocalDeploymentBuildFromProject(projectId, deploymentId);
        localDockerStackLifecycleService.startCluster(deploymentId);
        return projectService.deployProject(projectId, deploymentId);
    }

    public DeploymentResponse stop(String projectId) {
        ProjectResponse overview = projectService.getProject(projectId);
        AirflowDeployment deployment = findFlowDeckTestDeployment(overview.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No Flow Deck test environment for this tenant. "
                                + "Start it from Flow Deck IDE (Sync → Test environment) first."));
        return localDockerStackLifecycleService.stopCluster(deployment.getDeploymentId());
    }

    /**
     * Resolves the IDE test row whether it was created under the current configured name or a legacy name.
     */
    private Optional<AirflowDeployment> findFlowDeckTestDeployment(String tenantId) {
        Optional<AirflowDeployment> current = deploymentRepository.findByTenantTenantIdAndName(
                tenantId, testDeploymentName);
        if (current.isPresent()) {
            return current;
        }
        for (String legacy : LEGACY_TEST_DEPLOYMENT_NAMES) {
            if (legacy.equals(testDeploymentName)) {
                continue;
            }
            Optional<AirflowDeployment> row = deploymentRepository.findByTenantTenantIdAndName(tenantId, legacy);
            if (row.isPresent()) {
                return row;
            }
        }
        return Optional.empty();
    }

    private AirflowDeployment findOrCreateTestDeployment(String tenantId) {
        return findFlowDeckTestDeployment(tenantId).orElseGet(() -> {
            DeploymentCreateRequest req = new DeploymentCreateRequest();
            req.setTenantId(tenantId);
            req.setName(testDeploymentName);
            req.setDescription(testDeploymentDescription);
            req.setAirflowVersion(testAirflowVersion);
            req.setExecutorType(testExecutorType);
            try {
                DeploymentResponse created = airflowDeploymentService.createDeployment(req);
                return deploymentRepository.findByDeploymentId(created.getDeploymentId())
                        .orElseThrow(() -> new IllegalStateException("Deployment row missing after create"));
            } catch (Exception e) {
                return deploymentRepository.findByTenantTenantIdAndName(tenantId, testDeploymentName)
                        .orElseThrow(() -> new DeploymentException(
                                "Could not create Flow Deck test deployment \"" + testDeploymentName + "\": "
                                        + e.getMessage(), e));
            }
        });
    }
}

package com.airflow.platform.service;

import com.airflow.platform.dto.DeploymentCreateRequest;
import com.airflow.platform.dto.DeploymentResponse;
import com.airflow.platform.exception.DeploymentException;
import com.airflow.platform.exception.ResourceNotFoundException;
import com.airflow.platform.model.AirflowDeployment;
import com.airflow.platform.model.Tenant;
import com.airflow.platform.repository.AirflowDeploymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing Airflow deployments
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AirflowDeploymentService {

    private final AirflowDeploymentRepository deploymentRepository;
    private final TenantService tenantService;
    private final HelmService helmService;

    @Transactional
    public DeploymentResponse createDeployment(DeploymentCreateRequest request) {
        log.info("Creating Airflow deployment: {} for tenant: {}", request.getName(), request.getTenantId());

        // Get tenant
        Tenant tenant = tenantService.getTenantEntity(request.getTenantId());

        // Generate deployment ID
        String deploymentId = generateDeploymentId(request.getName());

        // Create deployment entity
        AirflowDeployment deployment = new AirflowDeployment();
        deployment.setDeploymentId(deploymentId);
        deployment.setTenant(tenant);
        deployment.setName(request.getName());
        deployment.setDescription(request.getDescription());
        deployment.setAirflowVersion(request.getAirflowVersion());
        deployment.setExecutorType(AirflowDeployment.ExecutorType.valueOf(request.getExecutorType().toUpperCase()));
        deployment.setStatus(AirflowDeployment.DeploymentStatus.PENDING);
        deployment.setNamespace(tenant.getKubernetesNamespace());
        deployment.setHelmReleaseName("airflow-" + deploymentId);
        deployment.setMinWorkers(request.getMinWorkers());
        deployment.setMaxWorkers(request.getMaxWorkers());
        deployment.setSchedulerCpu(request.getSchedulerCpu());
        deployment.setSchedulerMemory(request.getSchedulerMemory());
        deployment.setWorkerCpu(request.getWorkerCpu());
        deployment.setWorkerMemory(request.getWorkerMemory());
        deployment.setWebserverCpu(request.getWebserverCpu());
        deployment.setWebserverMemory(request.getWebserverMemory());
        deployment.setIngressHost(request.getIngressHost());
        deployment.setCustomConfig(request.getCustomConfig());

        deployment = deploymentRepository.save(deployment);

        // Deploy via Helm asynchronously
        final AirflowDeployment finalDeployment = deployment;
        try {
            deployment.setStatus(AirflowDeployment.DeploymentStatus.DEPLOYING);
            deploymentRepository.save(deployment);

            helmService.deployAirflow(deployment);

            deployment.setStatus(AirflowDeployment.DeploymentStatus.RUNNING);
            deployment.setDeployedAt(LocalDateTime.now());

            // Set webserver URL
            String webserverUrl = generateWebserverUrl(deployment);
            deployment.setWebserverUrl(webserverUrl);

            deployment = deploymentRepository.save(deployment);
            log.info("Airflow deployment created successfully: {}", deploymentId);

        } catch (Exception e) {
            log.error("Failed to deploy Airflow: {}", deploymentId, e);
            finalDeployment.setStatus(AirflowDeployment.DeploymentStatus.FAILED);
            deploymentRepository.save(finalDeployment);
            throw new DeploymentException("Failed to deploy Airflow: " + e.getMessage(), e);
        }

        return DeploymentResponse.fromEntity(deployment);
    }

    @Transactional(readOnly = true)
    public DeploymentResponse getDeployment(String deploymentId) {
        AirflowDeployment deployment = deploymentRepository.findByDeploymentId(deploymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + deploymentId));
        return DeploymentResponse.fromEntity(deployment);
    }

    @Transactional(readOnly = true)
    public List<DeploymentResponse> getDeploymentsByTenant(String tenantId) {
        return deploymentRepository.findByTenantTenantId(tenantId).stream()
                .map(DeploymentResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DeploymentResponse> getAllDeployments() {
        return deploymentRepository.findAll().stream()
                .map(DeploymentResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteDeployment(String deploymentId) {
        log.info("Deleting Airflow deployment: {}", deploymentId);

        AirflowDeployment deployment = deploymentRepository.findByDeploymentId(deploymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + deploymentId));

        try {
            helmService.uninstallAirflow(deployment);
            deployment.setStatus(AirflowDeployment.DeploymentStatus.DELETED);
            deploymentRepository.save(deployment);
            log.info("Airflow deployment deleted successfully: {}", deploymentId);
        } catch (Exception e) {
            log.error("Failed to delete Airflow deployment: {}", deploymentId, e);
            throw new DeploymentException("Failed to delete deployment: " + e.getMessage(), e);
        }
    }

    @Transactional
    public DeploymentResponse updateDeployment(String deploymentId, DeploymentCreateRequest request) {
        log.info("Updating Airflow deployment: {}", deploymentId);

        AirflowDeployment deployment = deploymentRepository.findByDeploymentId(deploymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + deploymentId));

        deployment.setName(request.getName());
        deployment.setDescription(request.getDescription());
        deployment.setMinWorkers(request.getMinWorkers());
        deployment.setMaxWorkers(request.getMaxWorkers());
        deployment.setSchedulerCpu(request.getSchedulerCpu());
        deployment.setSchedulerMemory(request.getSchedulerMemory());
        deployment.setWorkerCpu(request.getWorkerCpu());
        deployment.setWorkerMemory(request.getWorkerMemory());
        deployment.setWebserverCpu(request.getWebserverCpu());
        deployment.setWebserverMemory(request.getWebserverMemory());
        deployment.setCustomConfig(request.getCustomConfig());
        deployment.setStatus(AirflowDeployment.DeploymentStatus.UPDATING);

        deployment = deploymentRepository.save(deployment);

        try {
            helmService.upgradeAirflow(deployment);
            deployment.setStatus(AirflowDeployment.DeploymentStatus.RUNNING);
            deployment = deploymentRepository.save(deployment);
            log.info("Airflow deployment updated successfully: {}", deploymentId);
        } catch (Exception e) {
            log.error("Failed to update Airflow deployment: {}", deploymentId, e);
            deployment.setStatus(AirflowDeployment.DeploymentStatus.FAILED);
            deploymentRepository.save(deployment);
            throw new DeploymentException("Failed to update deployment: " + e.getMessage(), e);
        }

        return DeploymentResponse.fromEntity(deployment);
    }

    private String generateDeploymentId(String name) {
        String baseDeploymentId = name.toLowerCase()
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        String deploymentId = baseDeploymentId + "-" + UUID.randomUUID().toString().substring(0, 8);

        while (deploymentRepository.existsByDeploymentId(deploymentId)) {
            deploymentId = baseDeploymentId + "-" + UUID.randomUUID().toString().substring(0, 8);
        }

        return deploymentId;
    }

    private String generateWebserverUrl(AirflowDeployment deployment) {
        if (deployment.getIngressHost() != null && !deployment.getIngressHost().isEmpty()) {
            return "https://" + deployment.getIngressHost();
        }
        return "http://" + deployment.getHelmReleaseName() + "-webserver." + deployment.getNamespace() + ".svc.cluster.local:8080";
    }
}

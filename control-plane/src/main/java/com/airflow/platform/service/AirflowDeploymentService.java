package com.airflow.platform.service;

import com.airflow.platform.dto.DeploymentCreateRequest;
import com.airflow.platform.dto.DeploymentResponse;
import com.airflow.platform.config.SupportedAirflowVersions;
import com.airflow.platform.exception.DeploymentException;
import com.airflow.platform.exception.ResourceNotFoundException;
import com.airflow.platform.model.AirflowDeployment;
import com.airflow.platform.model.Tenant;
import com.airflow.platform.provider.DeploymentProvider;
import com.airflow.platform.provider.impl.LocalDeploymentProvider;
import com.airflow.platform.repository.AirflowDeploymentRepository;
import com.airflow.platform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing Airflow deployments
 * Supports multiple deployment providers (Kubernetes/Helm, AWS ECS, etc.)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AirflowDeploymentService {

    private final AirflowDeploymentRepository deploymentRepository;
    private final TenantService tenantService;

    @Autowired(required = false)
    private DeploymentProvider deploymentProvider;

    @Autowired(required = false)
    private ECSScalingManager ecsScalingManager;

    @Autowired(required = false)
    private LocalAirflowFabUserSyncService localAirflowFabUserSyncService;

    @Autowired(required = false)
    private LocalDeploymentStatusReconciler localDeploymentStatusReconciler;

    @Autowired(required = false)
    private LocalDeploymentProvider localDeploymentProvider;

    @Value("${local.auto-start-docker-on-create:true}")
    private boolean localAutoStartDockerOnCreate;

    @Transactional
    public DeploymentResponse createDeployment(DeploymentCreateRequest request) {
        log.info("Creating Airflow deployment: {} for tenant: {}", request.getName(), request.getTenantId());

        if (!SecurityUtils.isAdmin()) {
            String scope = SecurityUtils.getNonAdminTenantScope()
                    .orElseThrow(() -> new AccessDeniedException("Not authorized"));
            if (!scope.equals(request.getTenantId())) {
                throw new AccessDeniedException("Deployments must use your assigned tenant");
            }
        }

        SupportedAirflowVersions.requireSupported(request.getAirflowVersion());

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

        final AirflowDeployment finalDeployment = deployment;
        boolean deferLocalDocker = shouldDeferLocalDockerStart(request);
        if (deferLocalDocker) {
            try {
                localDeploymentProvider.provisionComposeArtifactsOnly(deployment);
                deployment.setStatus(AirflowDeployment.DeploymentStatus.STOPPED);
                deployment.setWebserverUrl(null);
                deployment = deploymentRepository.save(deployment);
                log.info("Airflow deployment registered locally without starting Docker: {}", deploymentId);
            } catch (Exception e) {
                log.error("Failed to provision local deployment artifacts: {}", deploymentId, e);
                finalDeployment.setStatus(AirflowDeployment.DeploymentStatus.FAILED);
                deploymentRepository.save(finalDeployment);
                throw new DeploymentException("Failed to provision local deployment: " + e.getMessage(), e);
            }
            return DeploymentResponse.fromEntity(deployment);
        }

        // Deploy via deployment provider (Helm/Kubernetes or ECS)
        try {
            deployment.setStatus(AirflowDeployment.DeploymentStatus.DEPLOYING);
            deploymentRepository.save(deployment);

            deploymentProvider.deploy(deployment);

            // Configure auto-scaling for ECS if applicable
            if (ecsScalingManager != null && "ecs".equals(deploymentProvider.getProviderType())) {
                ecsScalingManager.configureAutoScaling(deployment);
            }

            deployment.setStatus(AirflowDeployment.DeploymentStatus.RUNNING);
            deployment.setDeployedAt(LocalDateTime.now());

            // Set webserver URL
            String webserverUrl = deploymentProvider.getWebserverUrl(deployment);
            deployment.setWebserverUrl(webserverUrl);

            deployment = deploymentRepository.save(deployment);
            log.info("Airflow deployment created successfully: {}", deploymentId);

            if (localAirflowFabUserSyncService != null && deploymentProvider != null
                    && "local".equals(deploymentProvider.getProviderType())) {
                final String syncDeploymentId = deployment.getDeploymentId();
                Runnable scheduleFab = () -> localAirflowFabUserSyncService.schedulePostDeployFabSync(syncDeploymentId);
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            scheduleFab.run();
                        }
                    });
                } else {
                    scheduleFab.run();
                }
            }

        } catch (Exception e) {
            log.error("Failed to deploy Airflow: {}", deploymentId, e);
            finalDeployment.setStatus(AirflowDeployment.DeploymentStatus.FAILED);
            deploymentRepository.save(finalDeployment);
            throw new DeploymentException("Failed to deploy Airflow: " + e.getMessage(), e);
        }

        return DeploymentResponse.fromEntity(deployment);
    }

    private boolean shouldDeferLocalDockerStart(DeploymentCreateRequest request) {
        if (deploymentProvider == null || localDeploymentProvider == null) {
            return false;
        }
        if (!"local".equals(deploymentProvider.getProviderType())) {
            return false;
        }
        if (request.getDeferDockerStart() != null) {
            return Boolean.TRUE.equals(request.getDeferDockerStart());
        }
        return !localAutoStartDockerOnCreate;
    }

    @Transactional(readOnly = true)
    public DeploymentResponse getDeployment(String deploymentId) {
        if (localDeploymentStatusReconciler != null) {
            localDeploymentStatusReconciler.reconcileIfPendingOrDeploying(deploymentId);
        }
        AirflowDeployment deployment = deploymentRepository.findByDeploymentId(deploymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + deploymentId));
        assertDeploymentAccess(deployment);
        return DeploymentResponse.fromEntity(deployment);
    }

    @Transactional(readOnly = true)
    public List<DeploymentResponse> getDeploymentsByTenant(String tenantId) {
        if (localDeploymentStatusReconciler != null) {
            localDeploymentStatusReconciler.reconcilePendingOrDeployingDeployments();
        }
        return deploymentRepository.findByTenantTenantId(tenantId).stream()
                .map(DeploymentResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DeploymentResponse> getDeploymentsByTenantForCaller(String tenantId) {
        if (!SecurityUtils.isAdmin()) {
            String scope = SecurityUtils.getNonAdminTenantScope()
                    .orElseThrow(() -> new AccessDeniedException("Not authorized"));
            if (!scope.equals(tenantId)) {
                throw new AccessDeniedException("Not authorized");
            }
        }
        return getDeploymentsByTenant(tenantId);
    }

    @Transactional(readOnly = true)
    public List<DeploymentResponse> getAllDeployments() {
        if (localDeploymentStatusReconciler != null) {
            localDeploymentStatusReconciler.reconcilePendingOrDeployingDeployments();
        }
        return deploymentRepository.findAll().stream()
                .map(DeploymentResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DeploymentResponse> getDeploymentsForCurrentUser() {
        if (SecurityUtils.isAdmin()) {
            return getAllDeployments();
        }
        String tenantId = SecurityUtils.getNonAdminTenantScope()
                .orElseThrow(() -> new AccessDeniedException("Not authorized"));
        return getDeploymentsByTenant(tenantId);
    }

    @Transactional
    public void deleteDeployment(String deploymentId) {
        log.info("Deleting Airflow deployment: {}", deploymentId);

        AirflowDeployment deployment = deploymentRepository.findByDeploymentId(deploymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + deploymentId));
        assertDeploymentAccess(deployment);

        try {
            // Remove auto-scaling for ECS if applicable
            if (ecsScalingManager != null && "ecs".equals(deploymentProvider.getProviderType())) {
                ecsScalingManager.removeAutoScaling(deployment);
            }

            deploymentProvider.uninstall(deployment);
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
        assertDeploymentAccess(deployment);

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
            deploymentProvider.upgrade(deployment);

            // Update auto-scaling for ECS if applicable
            if (ecsScalingManager != null && "ecs".equals(deploymentProvider.getProviderType())) {
                ecsScalingManager.updateAutoScaling(deployment, request.getMinWorkers(), request.getMaxWorkers());
            }

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

    private void assertDeploymentAccess(AirflowDeployment deployment) {
        SecurityUtils.assertTenantInScope(deployment.getTenant().getTenantId());
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

}

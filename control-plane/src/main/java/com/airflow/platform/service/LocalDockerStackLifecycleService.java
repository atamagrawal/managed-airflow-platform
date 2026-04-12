package com.airflow.platform.service;

import com.airflow.platform.dto.DeploymentResponse;
import com.airflow.platform.exception.DeploymentException;
import com.airflow.platform.exception.ResourceNotFoundException;
import com.airflow.platform.model.AirflowDeployment;
import com.airflow.platform.provider.impl.LocalDeploymentProvider;
import com.airflow.platform.repository.AirflowDeploymentRepository;
import com.airflow.platform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Start/stop local Docker Compose Airflow stacks on demand and auto-stop after idle timeout.
 */
@Service
@ConditionalOnProperty(name = "deployment.provider", havingValue = "local")
@RequiredArgsConstructor
@Slf4j
public class LocalDockerStackLifecycleService {

    private final AirflowDeploymentRepository deploymentRepository;
    private final LocalDeploymentProvider localDeploymentProvider;
    private final LocalDeploymentCommittedStatusService committedStatusService;

    @Autowired(required = false)
    private LocalAirflowFabUserSyncService localAirflowFabUserSyncService;

    @Value("${local.test-cluster-idle-timeout-minutes:60}")
    private long testClusterIdleTimeoutMinutes;

    public DeploymentResponse startCluster(String deploymentId) {
        if (committedStatusService.markDeployingUnlessAlreadyRunning(deploymentId)) {
            return DeploymentResponse.fromEntity(
                    deploymentRepository.findByDeploymentId(deploymentId).orElseThrow());
        }
        try {
            AirflowDeployment deployment = deploymentRepository.findByDeploymentId(deploymentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + deploymentId));
            localDeploymentProvider.startComposeStack(deployment);
            committedStatusService.markRunningAfterComposeStart(deploymentId);
            scheduleFabSyncAfterCommit(deploymentId);
            log.info("Started local test cluster for deployment {}", deploymentId);
        } catch (Exception e) {
            committedStatusService.markFailedAfterComposeStart(deploymentId);
            if (e instanceof DeploymentException) {
                throw (DeploymentException) e;
            }
            throw new DeploymentException("Failed to start local stack: " + e.getMessage(), e);
        }
        return DeploymentResponse.fromEntity(deploymentRepository.findByDeploymentId(deploymentId).orElseThrow());
    }

    @Transactional
    public DeploymentResponse stopCluster(String deploymentId) {
        AirflowDeployment deployment = requireDeploymentWithAccess(deploymentId);
        try {
            localDeploymentProvider.stopComposeStack(deployment);
        } catch (Exception e) {
            throw new DeploymentException("Failed to stop local stack: " + e.getMessage(), e);
        }
        deployment.setStatus(AirflowDeployment.DeploymentStatus.STOPPED);
        deployment.setWebserverUrl(null);
        deployment = deploymentRepository.save(deployment);
        log.info("Stopped local test cluster for deployment {}", deploymentId);
        return DeploymentResponse.fromEntity(deployment);
    }

    /**
     * Records activity for idle timeout. Enforces tenant scope (call from authenticated API paths).
     */
    @Transactional
    public void recordUserActivity(String deploymentId) {
        AirflowDeployment d = deploymentRepository.findByDeploymentId(deploymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + deploymentId));
        SecurityUtils.assertTenantInScope(d.getTenant().getTenantId());
        touchLastActivity(d);
        deploymentRepository.save(d);
    }

    /**
     * Updates last-activity time only. Caller must have already verified authorization.
     */
    @Transactional
    public void touchLastActivityTrusted(String deploymentId) {
        deploymentRepository.findByDeploymentId(deploymentId).ifPresent(d -> {
            touchLastActivity(d);
            deploymentRepository.save(d);
        });
    }

    @Scheduled(fixedDelayString = "${local.idle-stop.check-interval-ms:60000}")
    @Transactional
    public void stopIdleLocalStacks() {
        if (testClusterIdleTimeoutMinutes <= 0) {
            return;
        }
        var previous = SecurityContextHolder.getContext().getAuthentication();
        try {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "local-idle-stop",
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            LocalDateTime cutoff = LocalDateTime.now().minusMinutes(testClusterIdleTimeoutMinutes);
            for (AirflowDeployment d : deploymentRepository.findAll()) {
                if (d.getStatus() != AirflowDeployment.DeploymentStatus.RUNNING) {
                    continue;
                }
                LocalDateTime lastActivity = d.getLocalStackLastActivityAt();
                if (lastActivity == null) {
                    lastActivity = d.getDeployedAt() != null ? d.getDeployedAt() : d.getCreatedAt();
                }
                if (lastActivity == null) {
                    continue;
                }
                if (lastActivity.isAfter(cutoff)) {
                    continue;
                }
                try {
                    log.info("Auto-stopping idle local deployment {} (last activity {})",
                            d.getDeploymentId(), d.getLocalStackLastActivityAt());
                    stopCluster(d.getDeploymentId());
                } catch (Exception e) {
                    log.warn("Idle stop failed for {}: {}", d.getDeploymentId(), e.getMessage());
                }
            }
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }

    private AirflowDeployment requireDeploymentWithAccess(String deploymentId) {
        AirflowDeployment d = deploymentRepository.findByDeploymentId(deploymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + deploymentId));
        SecurityUtils.assertTenantInScope(d.getTenant().getTenantId());
        return d;
    }

    private static void touchLastActivity(AirflowDeployment d) {
        d.setLocalStackLastActivityAt(LocalDateTime.now());
    }

    private void scheduleFabSyncAfterCommit(String deploymentId) {
        if (localAirflowFabUserSyncService == null) {
            return;
        }
        Runnable scheduleFab = () -> localAirflowFabUserSyncService.schedulePostDeployFabSync(deploymentId);
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
}

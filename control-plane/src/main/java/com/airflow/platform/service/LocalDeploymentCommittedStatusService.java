package com.airflow.platform.service;

import com.airflow.platform.exception.ResourceNotFoundException;
import com.airflow.platform.model.AirflowDeployment;
import com.airflow.platform.provider.impl.LocalDeploymentProvider;
import com.airflow.platform.repository.AirflowDeploymentRepository;
import com.airflow.platform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Persists deployment status in separate transactions so long-running local Docker work does not hide
 * intermediate states from other API clients (e.g. DEPLOYING while {@code docker compose build/up} runs).
 */
@Service
@ConditionalOnBean(LocalDeploymentProvider.class)
@RequiredArgsConstructor
public class LocalDeploymentCommittedStatusService {

    private final AirflowDeploymentRepository deploymentRepository;
    private final LocalDeploymentProvider localDeploymentProvider;

    /**
     * @return {@code true} if the stack was already running and DB was synced; {@code false} if status was set to
     *         DEPLOYING and the caller should run {@link LocalDeploymentProvider#startComposeStack}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markDeployingUnlessAlreadyRunning(String deploymentId) {
        AirflowDeployment d = loadAndAssertAccess(deploymentId);
        String live = localDeploymentProvider.getDeploymentStatus(d);
        if ("RUNNING".equals(live)) {
            d.setStatus(AirflowDeployment.DeploymentStatus.RUNNING);
            d.setWebserverUrl(localDeploymentProvider.getWebserverUrl(d));
            touchLastActivity(d);
            deploymentRepository.save(d);
            return true;
        }
        d.setStatus(AirflowDeployment.DeploymentStatus.DEPLOYING);
        deploymentRepository.save(d);
        return false;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRunningAfterComposeStart(String deploymentId) {
        AirflowDeployment d = loadAndAssertAccess(deploymentId);
        d.setStatus(AirflowDeployment.DeploymentStatus.RUNNING);
        d.setWebserverUrl(localDeploymentProvider.getWebserverUrl(d));
        if (d.getDeployedAt() == null) {
            d.setDeployedAt(LocalDateTime.now());
        }
        touchLastActivity(d);
        deploymentRepository.save(d);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailedAfterComposeStart(String deploymentId) {
        AirflowDeployment d = loadAndAssertAccess(deploymentId);
        d.setStatus(AirflowDeployment.DeploymentStatus.FAILED);
        deploymentRepository.save(d);
    }

    private static void touchLastActivity(AirflowDeployment d) {
        d.setLocalStackLastActivityAt(LocalDateTime.now());
    }

    private AirflowDeployment loadAndAssertAccess(String deploymentId) {
        AirflowDeployment d = deploymentRepository.findByDeploymentId(deploymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + deploymentId));
        SecurityUtils.assertTenantInScope(d.getTenant().getTenantId());
        return d;
    }
}

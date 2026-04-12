package com.airflow.platform.service;

import com.airflow.platform.model.AirflowDeployment;
import com.airflow.platform.provider.impl.LocalDeploymentProvider;
import com.airflow.platform.repository.AirflowDeploymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

/**
 * Aligns control-plane DB status with actual Docker Compose state for local deployments.
 * <p>
 * If the process was interrupted or the UI only refreshes slowly, rows can stay {@code DEPLOYING} while
 * {@code airflow-apiserver} is already running — this self-heals on read.
 */
@Service
@ConditionalOnProperty(name = "deployment.provider", havingValue = "local")
@RequiredArgsConstructor
@Slf4j
public class LocalDeploymentStatusReconciler {

    private static final EnumSet<AirflowDeployment.DeploymentStatus> RECONCILE_FROM =
            EnumSet.of(AirflowDeployment.DeploymentStatus.PENDING, AirflowDeployment.DeploymentStatus.DEPLOYING);

    private final AirflowDeploymentRepository deploymentRepository;
    private final LocalDeploymentProvider localDeploymentProvider;

    /**
     * Commits in a separate transaction so callers can stay {@code readOnly = true} and still see fresh rows
     * on the following query.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcilePendingOrDeployingDeployments() {
        List<AirflowDeployment> candidates = deploymentRepository.findByStatusIn(RECONCILE_FROM);
        if (candidates.isEmpty()) {
            return;
        }
        for (AirflowDeployment d : candidates) {
            try {
                String live = localDeploymentProvider.getDeploymentStatus(d);
                if (!"RUNNING".equals(live)) {
                    continue;
                }
                d.setStatus(AirflowDeployment.DeploymentStatus.RUNNING);
                if (!StringUtils.hasText(d.getWebserverUrl())) {
                    d.setWebserverUrl(localDeploymentProvider.getWebserverUrl(d));
                }
                if (d.getDeployedAt() == null) {
                    d.setDeployedAt(LocalDateTime.now());
                }
                if (d.getLocalStackLastActivityAt() == null) {
                    d.setLocalStackLastActivityAt(idleBaselineForLocalStack(d));
                }
                deploymentRepository.save(d);
                log.info("Reconciled deployment {} to RUNNING (apiserver HTTP health OK)", d.getDeploymentId());
            } catch (Exception e) {
                log.debug("Status reconcile skipped for {}: {}", d.getDeploymentId(), e.getMessage());
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcileIfPendingOrDeploying(String deploymentId) {
        AirflowDeployment d = deploymentRepository.findByDeploymentId(deploymentId).orElse(null);
        if (d == null || !RECONCILE_FROM.contains(d.getStatus())) {
            return;
        }
        try {
            String live = localDeploymentProvider.getDeploymentStatus(d);
            if (!"RUNNING".equals(live)) {
                return;
            }
            d.setStatus(AirflowDeployment.DeploymentStatus.RUNNING);
            if (!StringUtils.hasText(d.getWebserverUrl())) {
                d.setWebserverUrl(localDeploymentProvider.getWebserverUrl(d));
            }
            if (d.getDeployedAt() == null) {
                d.setDeployedAt(LocalDateTime.now());
            }
            if (d.getLocalStackLastActivityAt() == null) {
                d.setLocalStackLastActivityAt(idleBaselineForLocalStack(d));
            }
            deploymentRepository.save(d);
            log.info("Reconciled deployment {} to RUNNING (apiserver HTTP health OK)", deploymentId);
        } catch (Exception e) {
            log.debug("Status reconcile skipped for {}: {}", deploymentId, e.getMessage());
        }
    }

    /**
     * When promoting to RUNNING, seed idle tracking if missing so auto-stop can apply (same fallback as
     * {@link LocalDockerStackLifecycleService}).
     */
    private static LocalDateTime idleBaselineForLocalStack(AirflowDeployment d) {
        if (d.getDeployedAt() != null) {
            return d.getDeployedAt();
        }
        if (d.getCreatedAt() != null) {
            return d.getCreatedAt();
        }
        return LocalDateTime.now();
    }
}

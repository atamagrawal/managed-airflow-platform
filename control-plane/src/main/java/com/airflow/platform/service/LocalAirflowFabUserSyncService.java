package com.airflow.platform.service;

import com.airflow.platform.config.PlatformSecurityProperties;
import com.airflow.platform.model.AirflowDeployment;
import com.airflow.platform.model.PlatformUser;
import com.airflow.platform.provider.impl.LocalDeploymentProvider;
import com.airflow.platform.repository.AirflowDeploymentRepository;
import com.airflow.platform.repository.PlatformUserRepository;
import com.airflow.platform.security.AirflowBootstrapCrypto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.airflow.platform.config.AsyncConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Keeps Airflow FAB users aligned with platform accounts on {@code local} docker-compose deployments.
 */
@Service
@ConditionalOnProperty(name = "deployment.provider", havingValue = "local")
@RequiredArgsConstructor
@Slf4j
public class LocalAirflowFabUserSyncService {

    private final AirflowDeploymentRepository deploymentRepository;
    private final PlatformUserRepository platformUserRepository;
    private final LocalDeploymentProvider localDeploymentProvider;
    private final AirflowBootstrapCrypto airflowBootstrapCrypto;
    private final PlatformSecurityProperties securityProperties;

    /**
     * After each successful FlowDeck login, push this user to every relevant RUNNING local deployment.
     */
    public void syncUserOnLogin(String username, String plainPassword, boolean platformAdmin, String tenantScope) {
        String tenantFilter = platformAdmin ? null : tenantScope;
        syncUserToDeployments(tenantFilter, username, plainPassword, platformAdmin);
    }

    /**
     * Runs {@link #syncAllTenantUsersToDeployment(AirflowDeployment)} in the background after the deployment is up.
     * Avoids blocking HTTP/bootstrap on slow {@code docker compose exec airflow ...} cold starts.
     * <p>
     * {@code @Transactional} keeps a Hibernate session open for lazy {@link AirflowDeployment#getTenant()} (async thread
     * has no enclosing persistence context otherwise).
     */
    @Async(AsyncConfig.LOCAL_FAB_SYNC_EXECUTOR)
    @Transactional(readOnly = true)
    public void schedulePostDeployFabSync(String deploymentId) {
        log.info("Background FAB user sync starting for deployment {}", deploymentId);
        try {
            AirflowDeployment d = deploymentRepository.findByDeploymentId(deploymentId).orElse(null);
            if (d == null) {
                log.warn("Background FAB sync skipped: deployment {} not found", deploymentId);
                return;
            }
            if (d.getStatus() != AirflowDeployment.DeploymentStatus.RUNNING) {
                log.info("Background FAB sync skipped for {} (status={})", deploymentId, d.getStatus());
                return;
            }
            syncAllTenantUsersToDeployment(d);
            log.info("Background FAB user sync finished for deployment {}", deploymentId);
        } catch (Exception e) {
            log.warn("Background FAB sync failed for {}: {}", deploymentId, e.getMessage());
        }
    }

    /**
     * When a new deployment is created for a tenant, provision every known platform account for that tenant
     * (uses encrypted bootstrap secrets and optional {@code {noop}} YAML users).
     */
    public void syncAllTenantUsersToDeployment(AirflowDeployment deployment) {
        if (deployment.getStatus() != AirflowDeployment.DeploymentStatus.RUNNING) {
            return;
        }
        String tenantId = deployment.getTenant().getTenantId();
        Set<String> done = new LinkedHashSet<>();

        for (PlatformUser u : listDatabaseUsersForTenant(tenantId)) {
            String key = u.getUsername().toLowerCase(Locale.ROOT);
            if (!done.add(key)) {
                continue;
            }
            syncOneDbUserToDeployment(deployment, u, isPlatformAdmin(u));
        }

        for (PlatformUser u : platformUserRepository.findAll()) {
            if (!u.isEnabled() || !isPlatformAdmin(u)) {
                continue;
            }
            String key = u.getUsername().toLowerCase(Locale.ROOT);
            if (!done.add(key)) {
                continue;
            }
            syncOneDbUserToDeployment(deployment, u, true);
        }

        syncYamlNoopUsersForTenant(deployment, tenantId, done);
    }

    private List<PlatformUser> listDatabaseUsersForTenant(String tenantId) {
        Set<Long> seen = new LinkedHashSet<>();
        List<PlatformUser> out = new ArrayList<>();
        for (PlatformUser u : platformUserRepository.findByHomeTenantIdAndEnabledIsTrue(tenantId)) {
            if (seen.add(u.getId())) {
                out.add(u);
            }
        }
        String def = securityProperties.getDefaultTenantIdForUsers();
        if (StringUtils.hasText(def) && def.trim().equals(tenantId)) {
            for (PlatformUser u : platformUserRepository.findByHomeTenantIdIsNullAndEnabledIsTrue()) {
                if (!u.isEnabled() || isPlatformAdmin(u)) {
                    continue;
                }
                if (seen.add(u.getId())) {
                    out.add(u);
                }
            }
        }
        return out;
    }

    private void syncYamlNoopUsersForTenant(AirflowDeployment deployment, String tenantId, Set<String> done) {
        for (Map.Entry<String, PlatformSecurityProperties.UserAccountProperties> e : securityProperties.getUsers().entrySet()) {
            PlatformSecurityProperties.UserAccountProperties a = e.getValue();
            String pwd = a.getPassword();
            if (pwd == null || !pwd.startsWith("{noop}")) {
                continue;
            }
            String homeTenant = StringUtils.hasText(a.getTenantId())
                    ? a.getTenantId().trim()
                    : securityProperties.getDefaultTenantIdForUsers();
            if (!StringUtils.hasText(homeTenant) || !tenantId.equals(homeTenant.trim())) {
                continue;
            }
            String yamlUsername = e.getKey();
            String key = yamlUsername.toLowerCase(Locale.ROOT);
            if (!done.add(key)) {
                continue;
            }
            boolean admin = normalizeYamlRoles(a).stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r));
            String plain = pwd.substring("{noop}".length());
            try {
                localDeploymentProvider.ensureFabUserMatchesPlatform(deployment, yamlUsername.trim(), plain, admin);
            } catch (Exception ex) {
                log.warn("FAB sync skipped for YAML user {} on deployment {}: {}", yamlUsername,
                        deployment.getDeploymentId(), ex.getMessage());
            }
        }
    }

    private static List<String> normalizeYamlRoles(PlatformSecurityProperties.UserAccountProperties a) {
        List<String> roles = a.getRoles();
        if (roles == null || roles.isEmpty()) {
            return List.of("USER");
        }
        return roles.stream()
                .filter(r -> r != null && !r.isBlank())
                .map(r -> r.trim().toUpperCase(Locale.ROOT))
                .toList();
    }

    private void syncOneDbUserToDeployment(AirflowDeployment deployment, PlatformUser u, boolean fabAdmin) {
        String cipher = u.getAirflowBootstrapSecret();
        if (!StringUtils.hasText(cipher)) {
            log.debug("Skipping FAB sync for {} on deployment {} (no bootstrap secret yet — sign in to FlowDeck once)",
                    u.getUsername(), deployment.getDeploymentId());
            return;
        }
        try {
            String plain = airflowBootstrapCrypto.decrypt(cipher);
            localDeploymentProvider.ensureFabUserMatchesPlatform(deployment, u.getUsername(), plain, fabAdmin);
        } catch (Exception e) {
            log.warn("Failed to sync user {} to deployment {}: {}", u.getUsername(), deployment.getDeploymentId(),
                    e.getMessage());
        }
    }

    private static boolean isPlatformAdmin(PlatformUser u) {
        return PlatformUserService.parseRolesCsv(u.getRolesCsv()).stream()
                .anyMatch(r -> "ADMIN".equalsIgnoreCase(r));
    }

    /**
     * @param tenantId when {@code null}, all deployments are considered (platform admin creating a user or signing in).
     */
    public void syncUserToDeployments(String tenantId, String username, String plainPassword, boolean platformAdmin) {
        List<AirflowDeployment> deployments = tenantId == null || tenantId.isBlank()
                ? deploymentRepository.findAll()
                : deploymentRepository.findByTenantTenantId(tenantId.trim());
        for (AirflowDeployment d : deployments) {
            if (d.getStatus() != AirflowDeployment.DeploymentStatus.RUNNING) {
                continue;
            }
            try {
                localDeploymentProvider.ensureFabUserMatchesPlatform(d, username, plainPassword, platformAdmin);
            } catch (Exception e) {
                log.warn("FAB sync skipped for user {} on deployment {}: {}", username, d.getDeploymentId(),
                        e.getMessage());
            }
        }
    }
}

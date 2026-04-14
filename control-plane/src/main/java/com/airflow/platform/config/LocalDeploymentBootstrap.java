package com.airflow.platform.config;

import com.airflow.platform.dto.DeploymentCreateRequest;
import com.airflow.platform.repository.AirflowDeploymentRepository;
import com.airflow.platform.repository.TenantRepository;
import com.airflow.platform.service.AirflowDeploymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Optionally creates a default Airflow deployment on startup for local development.
 * Disabled by default ({@code bootstrap.default-deployment.enabled=false}); enable only if you want an automatic row.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "deployment.provider", havingValue = "local")
public class LocalDeploymentBootstrap {

    private final TenantRepository tenantRepository;
    private final AirflowDeploymentRepository deploymentRepository;
    private final AirflowDeploymentService airflowDeploymentService;

    @Value("${bootstrap.default-deployment.enabled:false}")
    private boolean enabled;

    @Value("${bootstrap.default-tenant.tenant-id:local-default}")
    private String defaultTenantId;

    @Value("${bootstrap.default-deployment.name:Local Default Deployment}")
    private String defaultDeploymentName;

    @Value("${bootstrap.default-deployment.description:Auto-created for local development}")
    private String defaultDeploymentDescription;

    @Value("${bootstrap.default-deployment.airflow-version:3.2.0}")
    private String defaultAirflowVersion;

    @Value("${bootstrap.default-deployment.executor-type:LOCAL}")
    private String defaultExecutorType;

    @EventListener(ApplicationReadyEvent.class)
    @Order(20)
    @Transactional
    public void ensureDefaultDeployment() {
        if (!enabled) {
            log.info("Default local deployment bootstrap is disabled");
            return;
        }

        if (!tenantRepository.existsByTenantId(defaultTenantId)) {
            log.warn("Default tenant {} not found; skipping default deployment bootstrap", defaultTenantId);
            return;
        }

        if (deploymentRepository.existsByTenantTenantIdAndName(defaultTenantId, defaultDeploymentName)) {
            log.info("Default deployment already exists for tenant {}: {}", defaultTenantId, defaultDeploymentName);
            return;
        }

        DeploymentCreateRequest request = new DeploymentCreateRequest();
        request.setTenantId(defaultTenantId);
        request.setName(defaultDeploymentName);
        request.setDescription(defaultDeploymentDescription);
        request.setAirflowVersion(defaultAirflowVersion);
        request.setExecutorType(defaultExecutorType);

        var previousAuth = SecurityContextHolder.getContext().getAuthentication();
        try {
            // createDeployment enforces JWT tenant scope for non-admins; there is no user during startup.
            var bootstrapAuth = new UsernamePasswordAuthenticationToken(
                    "local-deployment-bootstrap",
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            SecurityContextHolder.getContext().setAuthentication(bootstrapAuth);
            airflowDeploymentService.createDeployment(request);
            log.info("Default local deployment created: {} for tenant {}", defaultDeploymentName, defaultTenantId);
        } catch (Exception e) {
            log.error("Failed to initialize default local deployment for tenant {}", defaultTenantId, e);
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previousAuth);
        }
    }
}

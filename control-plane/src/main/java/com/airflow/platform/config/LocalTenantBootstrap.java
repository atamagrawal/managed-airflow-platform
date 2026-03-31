package com.airflow.platform.config;

import com.airflow.platform.model.Tenant;
import com.airflow.platform.provider.CloudProvider;
import com.airflow.platform.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a default tenant automatically for local development.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "deployment.provider", havingValue = "local")
public class LocalTenantBootstrap {

    private final TenantRepository tenantRepository;
    private final CloudProvider cloudProvider;

    @Value("${bootstrap.default-tenant.enabled:true}")
    private boolean enabled;

    @Value("${bootstrap.default-tenant.tenant-id:local-default}")
    private String defaultTenantId;

    @Value("${bootstrap.default-tenant.name:Local Default Tenant}")
    private String defaultTenantName;

    @Value("${bootstrap.default-tenant.email:local-default@managed-airflow.local}")
    private String defaultTenantEmail;

    @Value("${bootstrap.default-tenant.organization:Local Development}")
    private String defaultTenantOrganization;

    @Value("${bootstrap.default-tenant.region:local}")
    private String defaultTenantRegion;

    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    @Transactional
    public void ensureDefaultTenant() {
        if (!enabled) {
            log.info("Default local tenant bootstrap is disabled");
            return;
        }

        if (tenantRepository.existsByTenantId(defaultTenantId)) {
            log.info("Default tenant already exists: {}", defaultTenantId);
            return;
        }
        if (tenantRepository.existsByEmail(defaultTenantEmail)) {
            log.warn("Default tenant email already exists with different tenant id: {}", defaultTenantEmail);
            return;
        }

        Tenant tenant = new Tenant();
        tenant.setTenantId(defaultTenantId);
        tenant.setName(defaultTenantName);
        tenant.setEmail(defaultTenantEmail);
        tenant.setOrganization(defaultTenantOrganization);
        tenant.setCloudProvider("LOCAL");
        tenant.setClusterName("local");
        tenant.setRegion(defaultTenantRegion);
        tenant.setKubernetesNamespace("airflow-" + defaultTenantId);
        tenant.setStatus(Tenant.TenantStatus.PENDING);

        tenant = tenantRepository.save(tenant);
        try {
            cloudProvider.createNamespace(tenant);
            tenant.setStatus(Tenant.TenantStatus.ACTIVE);
            tenantRepository.save(tenant);
            log.info("Default local tenant created: {}", defaultTenantId);
        } catch (Exception e) {
            tenant.setStatus(Tenant.TenantStatus.PENDING);
            tenantRepository.save(tenant);
            log.error("Failed to initialize default local tenant: {}", defaultTenantId, e);
        }
    }
}

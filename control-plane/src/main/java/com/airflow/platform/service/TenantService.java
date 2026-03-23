package com.airflow.platform.service;

import com.airflow.platform.dto.TenantCreateRequest;
import com.airflow.platform.dto.TenantResponse;
import com.airflow.platform.exception.ResourceNotFoundException;
import com.airflow.platform.model.Tenant;
import com.airflow.platform.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing tenants
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService {

    private final TenantRepository tenantRepository;
    private final KubernetesService kubernetesService;

    @Transactional
    public TenantResponse createTenant(TenantCreateRequest request) {
        log.info("Creating tenant: {}", request.getName());

        // Validate unique email
        if (tenantRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + request.getEmail());
        }

        // Generate tenant ID
        String tenantId = generateTenantId(request.getName());

        // Create namespace
        String namespace = "airflow-" + tenantId;

        Tenant tenant = new Tenant();
        tenant.setTenantId(tenantId);
        tenant.setName(request.getName());
        tenant.setEmail(request.getEmail());
        tenant.setOrganization(request.getOrganization());
        tenant.setCloudProvider(request.getCloudProvider());
        tenant.setClusterName(request.getClusterName());
        tenant.setRegion(request.getRegion());
        tenant.setStatus(Tenant.TenantStatus.PENDING);
        tenant.setKubernetesNamespace(namespace);

        tenant = tenantRepository.save(tenant);

        try {
            // Create Kubernetes namespace for the tenant
            kubernetesService.createNamespace(namespace, tenantId);
            tenant.setStatus(Tenant.TenantStatus.ACTIVE);
            tenant = tenantRepository.save(tenant);
            log.info("Tenant created successfully: {}", tenantId);
        } catch (Exception e) {
            log.error("Failed to create namespace for tenant: {}", tenantId, e);
            tenant.setStatus(Tenant.TenantStatus.PENDING);
            tenantRepository.save(tenant);
        }

        return TenantResponse.fromEntity(tenant);
    }

    @Transactional(readOnly = true)
    public TenantResponse getTenant(String tenantId) {
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));
        return TenantResponse.fromEntity(tenant);
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> getAllTenants() {
        return tenantRepository.findAll().stream()
                .map(TenantResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteTenant(String tenantId) {
        log.info("Deleting tenant: {}", tenantId);
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        // Delete Kubernetes namespace
        try {
            kubernetesService.deleteNamespace(tenant.getKubernetesNamespace());
        } catch (Exception e) {
            log.error("Failed to delete namespace for tenant: {}", tenantId, e);
        }

        tenant.setStatus(Tenant.TenantStatus.DELETED);
        tenantRepository.save(tenant);
        log.info("Tenant deleted successfully: {}", tenantId);
    }

    private String generateTenantId(String name) {
        String baseTenantId = name.toLowerCase()
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        String tenantId = baseTenantId;
        int counter = 1;

        while (tenantRepository.existsByTenantId(tenantId)) {
            tenantId = baseTenantId + "-" + counter++;
        }

        return tenantId;
    }

    public Tenant getTenantEntity(String tenantId) {
        return tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));
    }
}

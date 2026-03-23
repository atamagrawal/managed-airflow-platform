package com.airflow.platform.provider;

import com.airflow.platform.model.Tenant;

import java.util.Map;

/**
 * Interface for cloud provider-specific operations
 * Supports multiple cloud platforms (AWS ECS, Kubernetes, etc.)
 */
public interface CloudProvider {

    /**
     * Create an isolated namespace/cluster for a tenant
     */
    void createNamespace(Tenant tenant);

    /**
     * Delete a tenant's namespace/cluster
     */
    void deleteNamespace(Tenant tenant);

    /**
     * Check if a namespace exists
     */
    boolean namespaceExists(String namespace);

    /**
     * Create a secret in the tenant's namespace
     */
    void createSecret(String namespace, String secretName, Map<String, String> data);

    /**
     * Get cloud-specific configuration
     */
    Map<String, String> getCloudSpecificConfig(Tenant tenant);

    /**
     * Get the provider type (e.g., "kubernetes", "ecs")
     */
    String getProviderType();
}

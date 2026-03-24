package com.airflow.platform.service;

import com.airflow.platform.exception.DeploymentException;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for managing Kubernetes resources
 */
@Service
@Slf4j
public class KubernetesService {

    private final ApiClient apiClient;
    private final CoreV1Api coreV1Api;

    public KubernetesService() throws IOException {
        this.apiClient = Config.defaultClient();
        this.coreV1Api = new CoreV1Api(apiClient);
    }

    /**
     * Create a Kubernetes namespace for a tenant
     */
    public void createNamespace(String namespace, String tenantId) {
        log.info("Creating namespace: {}", namespace);

        V1Namespace ns = new V1Namespace();
        V1ObjectMeta metadata = new V1ObjectMeta();
        metadata.setName(namespace);

        Map<String, String> labels = new HashMap<>();
        labels.put("app", "managed-airflow");
        labels.put("tenant-id", tenantId);
        labels.put("managed-by", "airflow-control-plane");
        metadata.setLabels(labels);

        ns.setMetadata(metadata);

        try {
            coreV1Api.createNamespace(ns);
            log.info("Namespace created successfully: {}", namespace);
        } catch (Exception e) {
            log.error("Failed to create namespace: {}", namespace, e);
            throw new DeploymentException("Failed to create namespace: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a Kubernetes namespace
     */
    public void deleteNamespace(String namespace) {
        log.info("Deleting namespace: {}", namespace);

        try {
            coreV1Api.deleteNamespace(namespace);
            log.info("Namespace deleted successfully: {}", namespace);
        } catch (Exception e) {
            log.error("Failed to delete namespace: {}", namespace, e);
            throw new DeploymentException("Failed to delete namespace: " + e.getMessage(), e);
        }
    }

    /**
     * Check if a namespace exists
     */
    public boolean namespaceExists(String namespace) {
        try {
            coreV1Api.readNamespace(namespace);
            return true;
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("404")) {
                return false;
            }
            log.error("Error checking namespace existence: {}", namespace, e);
            throw new DeploymentException("Error checking namespace: " + e.getMessage(), e);
        }
    }

    /**
     * Create a secret in the specified namespace
     */
    public void createSecret(String namespace, String secretName, Map<String, String> data) {
        log.info("Creating secret {} in namespace: {}", secretName, namespace);

        V1Secret secret = new V1Secret();
        V1ObjectMeta metadata = new V1ObjectMeta();
        metadata.setName(secretName);
        metadata.setNamespace(namespace);
        secret.setMetadata(metadata);
        secret.setStringData(data);

        try {
            coreV1Api.createNamespacedSecret(namespace, secret);
            log.info("Secret created successfully: {}", secretName);
        } catch (Exception e) {
            log.error("Failed to create secret: {}", secretName, e);
            throw new DeploymentException("Failed to create secret: " + e.getMessage(), e);
        }
    }

    /**
     * Get API client for advanced operations
     */
    public ApiClient getApiClient() {
        return apiClient;
    }
}

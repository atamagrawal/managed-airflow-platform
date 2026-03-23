package com.airflow.platform.service;

import com.airflow.platform.exception.DeploymentException;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
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
            coreV1Api.createNamespace(ns, null, null, null, null);
            log.info("Namespace created successfully: {}", namespace);
        } catch (ApiException e) {
            log.error("Failed to create namespace: {}", namespace, e);
            throw new DeploymentException("Failed to create namespace: " + e.getResponseBody(), e);
        }
    }

    /**
     * Delete a Kubernetes namespace
     */
    public void deleteNamespace(String namespace) {
        log.info("Deleting namespace: {}", namespace);

        try {
            coreV1Api.deleteNamespace(namespace, null, null, null, null, null, null);
            log.info("Namespace deleted successfully: {}", namespace);
        } catch (ApiException e) {
            log.error("Failed to delete namespace: {}", namespace, e);
            throw new DeploymentException("Failed to delete namespace: " + e.getResponseBody(), e);
        }
    }

    /**
     * Check if a namespace exists
     */
    public boolean namespaceExists(String namespace) {
        try {
            coreV1Api.readNamespace(namespace, null);
            return true;
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                return false;
            }
            log.error("Error checking namespace existence: {}", namespace, e);
            throw new DeploymentException("Error checking namespace: " + e.getResponseBody(), e);
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
            coreV1Api.createNamespacedSecret(namespace, secret, null, null, null, null);
            log.info("Secret created successfully: {}", secretName);
        } catch (ApiException e) {
            log.error("Failed to create secret: {}", secretName, e);
            throw new DeploymentException("Failed to create secret: " + e.getResponseBody(), e);
        }
    }

    /**
     * Get API client for advanced operations
     */
    public ApiClient getApiClient() {
        return apiClient;
    }
}

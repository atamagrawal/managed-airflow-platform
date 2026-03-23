package com.airflow.platform.provider.impl;

import com.airflow.platform.model.Tenant;
import com.airflow.platform.provider.CloudProvider;
import com.airflow.platform.service.KubernetesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Kubernetes implementation of CloudProvider
 */
@Component
@ConditionalOnProperty(name = "deployment.provider", havingValue = "kubernetes", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class KubernetesCloudProvider implements CloudProvider {

    private final KubernetesService kubernetesService;

    @Override
    public void createNamespace(Tenant tenant) {
        kubernetesService.createNamespace(tenant.getKubernetesNamespace(), tenant.getTenantId());
    }

    @Override
    public void deleteNamespace(Tenant tenant) {
        kubernetesService.deleteNamespace(tenant.getKubernetesNamespace());
    }

    @Override
    public boolean namespaceExists(String namespace) {
        return kubernetesService.namespaceExists(namespace);
    }

    @Override
    public void createSecret(String namespace, String secretName, Map<String, String> data) {
        kubernetesService.createSecret(namespace, secretName, data);
    }

    @Override
    public Map<String, String> getCloudSpecificConfig(Tenant tenant) {
        Map<String, String> config = new HashMap<>();
        config.put("namespace", tenant.getKubernetesNamespace());
        config.put("provider", "kubernetes");
        return config;
    }

    @Override
    public String getProviderType() {
        return "kubernetes";
    }
}

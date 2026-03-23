package com.airflow.platform.provider.impl;

import com.airflow.platform.model.AirflowDeployment;
import com.airflow.platform.provider.DeploymentProvider;
import com.airflow.platform.service.HelmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Helm/Kubernetes implementation of DeploymentProvider
 */
@Component
@ConditionalOnProperty(name = "deployment.provider", havingValue = "kubernetes", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class HelmDeploymentProvider implements DeploymentProvider {

    private final HelmService helmService;

    @Override
    public void deploy(AirflowDeployment deployment) {
        helmService.deployAirflow(deployment);
    }

    @Override
    public void upgrade(AirflowDeployment deployment) {
        helmService.upgradeAirflow(deployment);
    }

    @Override
    public void uninstall(AirflowDeployment deployment) {
        helmService.uninstallAirflow(deployment);
    }

    @Override
    public String getDeploymentStatus(AirflowDeployment deployment) {
        // In a real implementation, this would query Helm/Kubernetes for status
        return deployment.getStatus().name();
    }

    @Override
    public String getWebserverUrl(AirflowDeployment deployment) {
        if (deployment.getIngressHost() != null && !deployment.getIngressHost().isEmpty()) {
            return "https://" + deployment.getIngressHost();
        }
        return "http://" + deployment.getHelmReleaseName() + "-webserver." +
               deployment.getNamespace() + ".svc.cluster.local:8080";
    }

    @Override
    public void scale(AirflowDeployment deployment, int minWorkers, int maxWorkers) {
        deployment.setMinWorkers(minWorkers);
        deployment.setMaxWorkers(maxWorkers);
        upgrade(deployment);
    }

    @Override
    public String getProviderType() {
        return "helm";
    }
}

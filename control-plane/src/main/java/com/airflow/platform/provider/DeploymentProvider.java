package com.airflow.platform.provider;

import com.airflow.platform.model.AirflowDeployment;

/**
 * Interface for deployment provider-specific operations
 * Supports multiple deployment platforms (Helm/Kubernetes, ECS, etc.)
 */
public interface DeploymentProvider {

    /**
     * Deploy an Airflow instance
     */
    void deploy(AirflowDeployment deployment);

    /**
     * Upgrade an existing Airflow deployment
     */
    void upgrade(AirflowDeployment deployment);

    /**
     * Uninstall an Airflow deployment
     */
    void uninstall(AirflowDeployment deployment);

    /**
     * Get the deployment status
     */
    String getDeploymentStatus(AirflowDeployment deployment);

    /**
     * Get the webserver URL for a deployment
     */
    String getWebserverUrl(AirflowDeployment deployment);

    /**
     * Scale the deployment
     */
    void scale(AirflowDeployment deployment, int minWorkers, int maxWorkers);

    /**
     * Get the provider type (e.g., "helm", "ecs")
     */
    String getProviderType();
}

package com.airflow.platform.provider.impl;

import com.airflow.platform.exception.DeploymentException;
import com.airflow.platform.model.Tenant;
import com.airflow.platform.provider.CloudProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.*;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.*;

import java.util.HashMap;
import java.util.Map;

/**
 * AWS ECS implementation of CloudProvider
 * Manages ECS clusters and AWS Secrets Manager for tenant isolation
 */
@Component
@ConditionalOnProperty(name = "deployment.provider", havingValue = "ecs")
@RequiredArgsConstructor
@Slf4j
public class ECSCloudProvider implements CloudProvider {

    private final EcsClient ecsClient;
    private final SecretsManagerClient secretsManagerClient;

    @Value("${aws.ecs.cluster-prefix:managed-airflow}")
    private String clusterPrefix;

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Override
    public void createNamespace(Tenant tenant) {
        String clusterName = getClusterName(tenant);
        log.info("Creating ECS cluster: {}", clusterName);

        try {
            CreateClusterRequest request = CreateClusterRequest.builder()
                    .clusterName(clusterName)
                    .tags(
                            Tag.builder().key("app").value("managed-airflow").build(),
                            Tag.builder().key("tenant-id").value(tenant.getTenantId()).build(),
                            Tag.builder().key("managed-by").value("airflow-control-plane").build()
                    )
                    .capacityProviders("FARGATE", "FARGATE_SPOT")
                    .build();

            ecsClient.createCluster(request);
            log.info("ECS cluster created successfully: {}", clusterName);
        } catch (ClusterAlreadyExistsException e) {
            log.info("ECS cluster already exists: {}", clusterName);
        } catch (Exception e) {
            log.error("Failed to create ECS cluster: {}", clusterName, e);
            throw new DeploymentException("Failed to create ECS cluster: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteNamespace(Tenant tenant) {
        String clusterName = getClusterName(tenant);
        log.info("Deleting ECS cluster: {}", clusterName);

        try {
            // First, list and stop all services in the cluster
            ListServicesRequest listRequest = ListServicesRequest.builder()
                    .cluster(clusterName)
                    .build();

            ListServicesResponse listResponse = ecsClient.listServices(listRequest);

            for (String serviceArn : listResponse.serviceArns()) {
                DeleteServiceRequest deleteServiceRequest = DeleteServiceRequest.builder()
                        .cluster(clusterName)
                        .service(serviceArn)
                        .force(true)
                        .build();
                ecsClient.deleteService(deleteServiceRequest);
            }

            // Now delete the cluster
            DeleteClusterRequest request = DeleteClusterRequest.builder()
                    .cluster(clusterName)
                    .build();

            ecsClient.deleteCluster(request);
            log.info("ECS cluster deleted successfully: {}", clusterName);
        } catch (Exception e) {
            log.error("Failed to delete ECS cluster: {}", clusterName, e);
            throw new DeploymentException("Failed to delete ECS cluster: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean namespaceExists(String namespace) {
        try {
            DescribeClustersRequest request = DescribeClustersRequest.builder()
                    .clusters(namespace)
                    .build();

            DescribeClustersResponse response = ecsClient.describeClusters(request);

            return !response.clusters().isEmpty() &&
                   response.clusters().get(0).status().equalsIgnoreCase("ACTIVE");
        } catch (Exception e) {
            log.error("Error checking cluster existence: {}", namespace, e);
            return false;
        }
    }

    @Override
    public void createSecret(String namespace, String secretName, Map<String, String> data) {
        log.info("Creating secret {} in AWS Secrets Manager", secretName);

        try {
            // Convert map to JSON string
            StringBuilder secretString = new StringBuilder("{");
            data.forEach((key, value) -> {
                if (secretString.length() > 1) secretString.append(",");
                secretString.append("\"").append(key).append("\":\"").append(value).append("\"");
            });
            secretString.append("}");

            CreateSecretRequest request = CreateSecretRequest.builder()
                    .name(namespace + "/" + secretName)
                    .secretString(secretString.toString())
                    .tags(
                            software.amazon.awssdk.services.secretsmanager.model.Tag.builder()
                                    .key("namespace").value(namespace).build(),
                            software.amazon.awssdk.services.secretsmanager.model.Tag.builder()
                                    .key("managed-by").value("airflow-control-plane").build()
                    )
                    .build();

            secretsManagerClient.createSecret(request);
            log.info("Secret created successfully in AWS Secrets Manager: {}", secretName);
        } catch (ResourceExistsException e) {
            log.info("Secret already exists: {}", secretName);
        } catch (Exception e) {
            log.error("Failed to create secret: {}", secretName, e);
            throw new DeploymentException("Failed to create secret: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, String> getCloudSpecificConfig(Tenant tenant) {
        Map<String, String> config = new HashMap<>();
        config.put("cluster", getClusterName(tenant));
        config.put("provider", "ecs");
        config.put("region", awsRegion);
        return config;
    }

    @Override
    public String getProviderType() {
        return "ecs";
    }

    private String getClusterName(Tenant tenant) {
        return clusterPrefix + "-" + tenant.getTenantId();
    }
}

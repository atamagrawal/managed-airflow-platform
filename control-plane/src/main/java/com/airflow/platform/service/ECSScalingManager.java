package com.airflow.platform.service;

import com.airflow.platform.exception.DeploymentException;
import com.airflow.platform.model.AirflowDeployment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.applicationautoscaling.ApplicationAutoScalingClient;
import software.amazon.awssdk.services.applicationautoscaling.model.*;

/**
 * Manages auto-scaling for ECS services
 * Provides similar functionality to KEDA for Kubernetes
 */
@Service
@ConditionalOnProperty(name = "deployment.provider", havingValue = "ecs")
@RequiredArgsConstructor
@Slf4j
public class ECSScalingManager {

    private final ApplicationAutoScalingClient autoScalingClient;

    @Value("${aws.ecs.cluster-prefix:managed-airflow}")
    private String clusterPrefix;

    /**
     * Configure auto-scaling for a worker service
     */
    public void configureAutoScaling(AirflowDeployment deployment) {
        if (deployment.getExecutorType() != AirflowDeployment.ExecutorType.CELERY &&
            deployment.getExecutorType() != AirflowDeployment.ExecutorType.CELERY_KUBERNETES) {
            log.info("Auto-scaling not applicable for executor type: {}", deployment.getExecutorType());
            return;
        }

        String clusterName = getClusterName(deployment);
        String serviceName = deployment.getDeploymentId() + "-worker";
        String resourceId = String.format("service/%s/%s", clusterName, serviceName);

        try {
            // Register scalable target
            registerScalableTarget(resourceId, deployment.getMinWorkers(), deployment.getMaxWorkers());

            // Create scaling policy based on CPU utilization
            createCPUScalingPolicy(resourceId, serviceName);

            // Create scaling policy based on memory utilization
            createMemoryScalingPolicy(resourceId, serviceName);

            log.info("Auto-scaling configured for deployment: {}", deployment.getDeploymentId());
        } catch (Exception e) {
            log.error("Failed to configure auto-scaling for deployment: {}", deployment.getDeploymentId(), e);
            throw new DeploymentException("Failed to configure auto-scaling: " + e.getMessage(), e);
        }
    }

    /**
     * Update auto-scaling configuration
     */
    public void updateAutoScaling(AirflowDeployment deployment, int minWorkers, int maxWorkers) {
        String clusterName = getClusterName(deployment);
        String serviceName = deployment.getDeploymentId() + "-worker";
        String resourceId = String.format("service/%s/%s", clusterName, serviceName);

        try {
            registerScalableTarget(resourceId, minWorkers, maxWorkers);
            log.info("Auto-scaling updated for deployment: {}", deployment.getDeploymentId());
        } catch (Exception e) {
            log.error("Failed to update auto-scaling for deployment: {}", deployment.getDeploymentId(), e);
            throw new DeploymentException("Failed to update auto-scaling: " + e.getMessage(), e);
        }
    }

    /**
     * Remove auto-scaling configuration
     */
    public void removeAutoScaling(AirflowDeployment deployment) {
        String clusterName = getClusterName(deployment);
        String serviceName = deployment.getDeploymentId() + "-worker";
        String resourceId = String.format("service/%s/%s", clusterName, serviceName);

        try {
            // Delete scaling policies
            deleteScalingPolicy(resourceId, serviceName + "-cpu-scaling");
            deleteScalingPolicy(resourceId, serviceName + "-memory-scaling");

            // Deregister scalable target
            DeregisterScalableTargetRequest request = DeregisterScalableTargetRequest.builder()
                    .serviceNamespace(ServiceNamespace.ECS)
                    .resourceId(resourceId)
                    .scalableDimension(ScalableDimension.ECS_SERVICE_DESIRED_COUNT)
                    .build();

            autoScalingClient.deregisterScalableTarget(request);
            log.info("Auto-scaling removed for deployment: {}", deployment.getDeploymentId());
        } catch (Exception e) {
            log.warn("Failed to remove auto-scaling for deployment: {}", deployment.getDeploymentId(), e);
        }
    }

    private void registerScalableTarget(String resourceId, int minCapacity, int maxCapacity) {
        RegisterScalableTargetRequest request = RegisterScalableTargetRequest.builder()
                .serviceNamespace(ServiceNamespace.ECS)
                .resourceId(resourceId)
                .scalableDimension(ScalableDimension.ECS_SERVICE_DESIRED_COUNT)
                .minCapacity(minCapacity)
                .maxCapacity(maxCapacity)
                .build();

        autoScalingClient.registerScalableTarget(request);
        log.info("Registered scalable target: {}", resourceId);
    }

    private void createCPUScalingPolicy(String resourceId, String serviceName) {
        String policyName = serviceName + "-cpu-scaling";

        PutScalingPolicyRequest request = PutScalingPolicyRequest.builder()
                .serviceNamespace(ServiceNamespace.ECS)
                .resourceId(resourceId)
                .scalableDimension(ScalableDimension.ECS_SERVICE_DESIRED_COUNT)
                .policyName(policyName)
                .policyType(PolicyType.TARGET_TRACKING_SCALING)
                .targetTrackingScalingPolicyConfiguration(
                        TargetTrackingScalingPolicyConfiguration.builder()
                                .targetValue(70.0) // Target 70% CPU utilization
                                .predefinedMetricSpecification(
                                        PredefinedMetricSpecification.builder()
                                                .predefinedMetricType(MetricType.ECS_SERVICE_AVERAGE_CPU_UTILIZATION)
                                                .build()
                                )
                                .scaleInCooldown(300)
                                .scaleOutCooldown(60)
                                .build()
                )
                .build();

        autoScalingClient.putScalingPolicy(request);
        log.info("Created CPU scaling policy: {}", policyName);
    }

    private void createMemoryScalingPolicy(String resourceId, String serviceName) {
        String policyName = serviceName + "-memory-scaling";

        PutScalingPolicyRequest request = PutScalingPolicyRequest.builder()
                .serviceNamespace(ServiceNamespace.ECS)
                .resourceId(resourceId)
                .scalableDimension(ScalableDimension.ECS_SERVICE_DESIRED_COUNT)
                .policyName(policyName)
                .policyType(PolicyType.TARGET_TRACKING_SCALING)
                .targetTrackingScalingPolicyConfiguration(
                        TargetTrackingScalingPolicyConfiguration.builder()
                                .targetValue(80.0) // Target 80% memory utilization
                                .predefinedMetricSpecification(
                                        PredefinedMetricSpecification.builder()
                                                .predefinedMetricType(MetricType.ECS_SERVICE_AVERAGE_MEMORY_UTILIZATION)
                                                .build()
                                )
                                .scaleInCooldown(300)
                                .scaleOutCooldown(60)
                                .build()
                )
                .build();

        autoScalingClient.putScalingPolicy(request);
        log.info("Created memory scaling policy: {}", policyName);
    }

    private void deleteScalingPolicy(String resourceId, String policyName) {
        try {
            DeleteScalingPolicyRequest request = DeleteScalingPolicyRequest.builder()
                    .serviceNamespace(ServiceNamespace.ECS)
                    .resourceId(resourceId)
                    .scalableDimension(ScalableDimension.ECS_SERVICE_DESIRED_COUNT)
                    .policyName(policyName)
                    .build();

            autoScalingClient.deleteScalingPolicy(request);
            log.info("Deleted scaling policy: {}", policyName);
        } catch (Exception e) {
            log.warn("Failed to delete scaling policy: {}", policyName, e);
        }
    }

    private String getClusterName(AirflowDeployment deployment) {
        return clusterPrefix + "-" + deployment.getTenant().getTenantId();
    }
}

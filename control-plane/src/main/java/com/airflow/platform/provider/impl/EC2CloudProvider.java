package com.airflow.platform.provider.impl;

import com.airflow.platform.exception.DeploymentException;
import com.airflow.platform.model.Tenant;
import com.airflow.platform.provider.CloudProvider;
import com.airflow.platform.service.EC2CommandExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.util.*;

/**
 * AWS EC2 implementation of CloudProvider
 * Uses EC2 instances with Docker for tenant isolation
 */
@Component
@ConditionalOnProperty(name = "deployment.provider", havingValue = "ec2")
@RequiredArgsConstructor
@Slf4j
public class EC2CloudProvider implements CloudProvider {

    private final Ec2Client ec2Client;
    private final EC2CommandExecutor commandExecutor;

    @Value("${aws.ec2.ami-id}")
    private String amiId;

    @Value("${aws.ec2.instance-type:t3.medium}")
    private String instanceType;

    @Value("${aws.ec2.key-name}")
    private String keyName;

    @Value("${aws.ec2.subnet-id}")
    private String subnetId;

    @Value("${aws.ec2.security-group-id}")
    private String securityGroupId;

    @Value("${aws.ec2.iam-instance-profile:}")
    private String iamInstanceProfile;

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Override
    public void createNamespace(Tenant tenant) {
        log.info("Creating EC2 instance for tenant: {}", tenant.getTenantId());

        try {
            // Check if instance already exists
            String existingInstanceId = findInstanceByTenantId(tenant.getTenantId());
            if (existingInstanceId != null) {
                log.info("Instance already exists for tenant {}: {}", tenant.getTenantId(), existingInstanceId);
                return;
            }

            // Create EC2 instance
            String instanceId = createInstance(tenant);

            // Wait for instance to be running
            waitForInstanceRunning(instanceId);

            // Wait for SSM agent to be ready
            waitForSSMReady(instanceId);

            // Install Docker and Docker Compose
            setupDockerEnvironment(instanceId);

            log.info("EC2 instance created successfully for tenant {}: {}", tenant.getTenantId(), instanceId);

        } catch (Exception e) {
            log.error("Failed to create EC2 instance for tenant: {}", tenant.getTenantId(), e);
            throw new DeploymentException("Failed to create EC2 instance: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteNamespace(Tenant tenant) {
        log.info("Deleting EC2 instance for tenant: {}", tenant.getTenantId());

        try {
            String instanceId = findInstanceByTenantId(tenant.getTenantId());

            if (instanceId == null) {
                log.info("No instance found for tenant: {}", tenant.getTenantId());
                return;
            }

            // Terminate the instance
            TerminateInstancesRequest request = TerminateInstancesRequest.builder()
                    .instanceIds(instanceId)
                    .build();

            ec2Client.terminateInstances(request);
            log.info("EC2 instance terminated for tenant {}: {}", tenant.getTenantId(), instanceId);

        } catch (Exception e) {
            log.error("Failed to delete EC2 instance for tenant: {}", tenant.getTenantId(), e);
            throw new DeploymentException("Failed to delete EC2 instance: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean namespaceExists(String namespace) {
        try {
            String instanceId = findInstanceByTenantId(namespace);
            if (instanceId == null) {
                return false;
            }

            DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                    .instanceIds(instanceId)
                    .build();

            DescribeInstancesResponse response = ec2Client.describeInstances(request);

            if (response.reservations().isEmpty()) {
                return false;
            }

            Instance instance = response.reservations().get(0).instances().get(0);
            InstanceStateName state = instance.state().name();

            return state == InstanceStateName.RUNNING || state == InstanceStateName.PENDING;

        } catch (Exception e) {
            log.error("Error checking namespace existence: {}", namespace, e);
            return false;
        }
    }

    @Override
    public void createSecret(String namespace, String secretName, Map<String, String> data) {
        log.info("Creating secret {} for namespace: {}", secretName, namespace);

        try {
            String instanceId = findInstanceByTenantId(namespace);
            if (instanceId == null) {
                throw new DeploymentException("No instance found for tenant: " + namespace);
            }

            // Create a secrets directory
            List<String> commands = new ArrayList<>();
            commands.add("mkdir -p /opt/airflow/secrets");

            // Write each secret as a file
            data.forEach((key, value) -> {
                String filePath = "/opt/airflow/secrets/" + secretName + "_" + key;
                commands.add("echo '" + value + "' > " + filePath);
                commands.add("chmod 600 " + filePath);
            });

            commandExecutor.executeCommand(instanceId, commands);
            log.info("Secret created successfully: {}", secretName);

        } catch (Exception e) {
            log.error("Failed to create secret: {}", secretName, e);
            throw new DeploymentException("Failed to create secret: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, String> getCloudSpecificConfig(Tenant tenant) {
        Map<String, String> config = new HashMap<>();

        String instanceId = findInstanceByTenantId(tenant.getTenantId());
        if (instanceId != null) {
            config.put("instance-id", instanceId);
            config.put("instance-ip", getInstancePublicIp(instanceId));
        }

        config.put("provider", "ec2");
        config.put("region", awsRegion);

        return config;
    }

    @Override
    public String getProviderType() {
        return "ec2";
    }

    /**
     * Get the instance ID for a tenant
     */
    public String getInstanceIdForTenant(String tenantId) {
        return findInstanceByTenantId(tenantId);
    }

    private String createInstance(Tenant tenant) {
        // User data script to install SSM agent
        String userData = Base64.getEncoder().encodeToString(getUserDataScript().getBytes());

        RunInstancesRequest.Builder requestBuilder = RunInstancesRequest.builder()
                .imageId(amiId)
                .instanceType(instanceType)
                .keyName(keyName)
                .minCount(1)
                .maxCount(1)
                .subnetId(subnetId)
                .securityGroupIds(securityGroupId)
                .userData(userData)
                .tagSpecifications(
                        TagSpecification.builder()
                                .resourceType(ResourceType.INSTANCE)
                                .tags(
                                        Tag.builder().key("Name").value("airflow-" + tenant.getTenantId()).build(),
                                        Tag.builder().key("tenant-id").value(tenant.getTenantId()).build(),
                                        Tag.builder().key("managed-by").value("airflow-control-plane").build(),
                                        Tag.builder().key("app").value("managed-airflow").build()
                                )
                                .build()
                );

        // Add IAM instance profile if configured
        if (iamInstanceProfile != null && !iamInstanceProfile.isEmpty()) {
            requestBuilder.iamInstanceProfile(IamInstanceProfileSpecification.builder()
                    .name(iamInstanceProfile)
                    .build());
        }

        RunInstancesResponse response = ec2Client.runInstances(requestBuilder.build());
        return response.instances().get(0).instanceId();
    }

    private String findInstanceByTenantId(String tenantId) {
        try {
            DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                    .filters(
                            Filter.builder()
                                    .name("tag:tenant-id")
                                    .values(tenantId)
                                    .build(),
                            Filter.builder()
                                    .name("instance-state-name")
                                    .values("running", "pending", "stopping", "stopped")
                                    .build()
                    )
                    .build();

            DescribeInstancesResponse response = ec2Client.describeInstances(request);

            if (response.reservations().isEmpty() || response.reservations().get(0).instances().isEmpty()) {
                return null;
            }

            return response.reservations().get(0).instances().get(0).instanceId();

        } catch (Exception e) {
            log.error("Error finding instance for tenant: {}", tenantId, e);
            return null;
        }
    }

    private String getInstancePublicIp(String instanceId) {
        try {
            DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                    .instanceIds(instanceId)
                    .build();

            DescribeInstancesResponse response = ec2Client.describeInstances(request);

            if (response.reservations().isEmpty()) {
                return null;
            }

            Instance instance = response.reservations().get(0).instances().get(0);
            return instance.publicIpAddress();

        } catch (Exception e) {
            log.error("Error getting instance IP: {}", instanceId, e);
            return null;
        }
    }

    private void waitForInstanceRunning(String instanceId) throws InterruptedException {
        log.info("Waiting for instance {} to be running...", instanceId);

        DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                .instanceIds(instanceId)
                .build();

        int attempts = 0;
        int maxAttempts = 60; // 5 minutes

        while (attempts < maxAttempts) {
            DescribeInstancesResponse response = ec2Client.describeInstances(request);
            Instance instance = response.reservations().get(0).instances().get(0);

            if (instance.state().name() == InstanceStateName.RUNNING) {
                log.info("Instance {} is now running", instanceId);
                return;
            }

            Thread.sleep(5000);
            attempts++;
        }

        throw new DeploymentException("Instance did not reach running state within timeout");
    }

    private void waitForSSMReady(String instanceId) throws InterruptedException {
        log.info("Waiting for SSM agent to be ready on instance {}...", instanceId);

        // Wait additional time for SSM agent to register
        Thread.sleep(60000); // 1 minute

        log.info("SSM agent should be ready on instance {}", instanceId);
    }

    private void setupDockerEnvironment(String instanceId) {
        log.info("Setting up Docker environment on instance {}", instanceId);

        List<String> commands = Arrays.asList(
                "sudo yum update -y",
                "sudo yum install -y docker",
                "sudo service docker start",
                "sudo usermod -a -G docker ec2-user",
                "sudo systemctl enable docker",
                "sudo curl -L \"https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)\" -o /usr/local/bin/docker-compose",
                "sudo chmod +x /usr/local/bin/docker-compose",
                "sudo mkdir -p /opt/airflow",
                "sudo chown ec2-user:ec2-user /opt/airflow"
        );

        commandExecutor.executeCommand(instanceId, commands);
        log.info("Docker environment setup completed on instance {}", instanceId);
    }

    private String getUserDataScript() {
        return "#!/bin/bash\n" +
                "yum install -y amazon-ssm-agent\n" +
                "systemctl enable amazon-ssm-agent\n" +
                "systemctl start amazon-ssm-agent\n";
    }
}

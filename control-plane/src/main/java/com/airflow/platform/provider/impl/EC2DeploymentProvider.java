package com.airflow.platform.provider.impl;

import com.airflow.platform.exception.DeploymentException;
import com.airflow.platform.model.AirflowDeployment;
import com.airflow.platform.provider.DeploymentProvider;
import com.airflow.platform.service.DockerComposeGenerator;
import com.airflow.platform.service.EC2CommandExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * EC2 with Docker Compose implementation of DeploymentProvider
 * Uses Docker Compose to deploy Airflow on EC2 instances
 */
@Component
@ConditionalOnProperty(name = "deployment.provider", havingValue = "ec2")
@RequiredArgsConstructor
@Slf4j
public class EC2DeploymentProvider implements DeploymentProvider {

    private final EC2CloudProvider ec2CloudProvider;
    private final EC2CommandExecutor commandExecutor;
    private final DockerComposeGenerator composeGenerator;

    @Override
    public void deploy(AirflowDeployment deployment) {
        log.info("Deploying Airflow to EC2 with Docker: {}", deployment.getDeploymentId());

        try {
            String instanceId = ec2CloudProvider.getInstanceIdForTenant(deployment.getTenant().getTenantId());
            if (instanceId == null) {
                throw new DeploymentException("No EC2 instance found for tenant: " + deployment.getTenant().getTenantId());
            }

            // Generate docker-compose.yml
            String dockerCompose = composeGenerator.generateDockerCompose(deployment);
            String envFile = composeGenerator.generateEnvFile(deployment);

            // Create deployment directory
            String deploymentDir = "/opt/airflow/deployments/" + deployment.getDeploymentId();
            List<String> setupCommands = Arrays.asList(
                    "mkdir -p " + deploymentDir,
                    "mkdir -p " + deploymentDir + "/dags",
                    "mkdir -p " + deploymentDir + "/logs",
                    "mkdir -p " + deploymentDir + "/plugins"
            );
            commandExecutor.executeCommand(instanceId, setupCommands);

            // Copy docker-compose.yml to instance
            String composePath = deploymentDir + "/docker-compose.yml";
            commandExecutor.copyFileToInstance(instanceId, dockerCompose, composePath);

            // Copy .env file to instance
            String envPath = deploymentDir + "/.env";
            commandExecutor.copyFileToInstance(instanceId, envFile, envPath);

            // Deploy using docker-compose
            List<String> deployCommands = Arrays.asList(
                    "cd " + deploymentDir,
                    "sudo docker-compose up -d"
            );
            EC2CommandExecutor.CommandResult result = commandExecutor.executeCommand(instanceId, deployCommands);

            if (!result.isSuccess()) {
                throw new DeploymentException("Docker Compose deployment failed: " + result.getStderr());
            }

            log.info("Airflow deployed successfully to EC2: {}", deployment.getDeploymentId());

        } catch (Exception e) {
            log.error("Failed to deploy Airflow to EC2: {}", deployment.getDeploymentId(), e);
            throw new DeploymentException("EC2 deployment failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void upgrade(AirflowDeployment deployment) {
        log.info("Upgrading Airflow deployment on EC2: {}", deployment.getDeploymentId());

        try {
            String instanceId = ec2CloudProvider.getInstanceIdForTenant(deployment.getTenant().getTenantId());
            if (instanceId == null) {
                throw new DeploymentException("No EC2 instance found for tenant: " + deployment.getTenant().getTenantId());
            }

            // Regenerate docker-compose.yml with updated configuration
            String dockerCompose = composeGenerator.generateDockerCompose(deployment);
            String deploymentDir = "/opt/airflow/deployments/" + deployment.getDeploymentId();
            String composePath = deploymentDir + "/docker-compose.yml";

            // Update docker-compose.yml
            commandExecutor.copyFileToInstance(instanceId, dockerCompose, composePath);

            // Recreate containers with new configuration
            List<String> upgradeCommands = Arrays.asList(
                    "cd " + deploymentDir,
                    "sudo docker-compose up -d --force-recreate"
            );
            EC2CommandExecutor.CommandResult result = commandExecutor.executeCommand(instanceId, upgradeCommands);

            if (!result.isSuccess()) {
                throw new DeploymentException("Docker Compose upgrade failed: " + result.getStderr());
            }

            log.info("Airflow upgraded successfully on EC2: {}", deployment.getDeploymentId());

        } catch (Exception e) {
            log.error("Failed to upgrade Airflow on EC2: {}", deployment.getDeploymentId(), e);
            throw new DeploymentException("EC2 upgrade failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void uninstall(AirflowDeployment deployment) {
        log.info("Uninstalling Airflow from EC2: {}", deployment.getDeploymentId());

        try {
            String instanceId = ec2CloudProvider.getInstanceIdForTenant(deployment.getTenant().getTenantId());
            if (instanceId == null) {
                log.info("No EC2 instance found for tenant: {}", deployment.getTenant().getTenantId());
                return;
            }

            String deploymentDir = "/opt/airflow/deployments/" + deployment.getDeploymentId();

            // Stop and remove containers
            List<String> uninstallCommands = Arrays.asList(
                    "cd " + deploymentDir,
                    "sudo docker-compose down -v",
                    "cd /opt/airflow/deployments",
                    "sudo rm -rf " + deployment.getDeploymentId()
            );

            commandExecutor.executeCommand(instanceId, uninstallCommands);
            log.info("Airflow uninstalled successfully from EC2: {}", deployment.getDeploymentId());

        } catch (Exception e) {
            log.error("Failed to uninstall Airflow from EC2: {}", deployment.getDeploymentId(), e);
            throw new DeploymentException("EC2 uninstall failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getDeploymentStatus(AirflowDeployment deployment) {
        try {
            String instanceId = ec2CloudProvider.getInstanceIdForTenant(deployment.getTenant().getTenantId());
            if (instanceId == null) {
                return "UNKNOWN";
            }

            String deploymentDir = "/opt/airflow/deployments/" + deployment.getDeploymentId();

            // Check if containers are running
            List<String> statusCommands = Arrays.asList(
                    "cd " + deploymentDir,
                    "sudo docker-compose ps --services --filter \"status=running\" | wc -l"
            );

            EC2CommandExecutor.CommandResult result = commandExecutor.executeCommand(instanceId, statusCommands);

            if (result.isSuccess()) {
                int runningContainers = Integer.parseInt(result.getStdout().trim());
                if (runningContainers > 0) {
                    return "RUNNING";
                } else {
                    return "STOPPED";
                }
            }

            return "UNKNOWN";

        } catch (Exception e) {
            log.error("Failed to get deployment status: {}", deployment.getDeploymentId(), e);
            return "ERROR";
        }
    }

    @Override
    public String getWebserverUrl(AirflowDeployment deployment) {
        String instanceIp = ec2CloudProvider.getCloudSpecificConfig(deployment.getTenant()).get("instance-ip");

        if (instanceIp != null) {
            return "http://" + instanceIp + ":8080";
        }

        return "http://pending-instance:8080";
    }

    @Override
    public void scale(AirflowDeployment deployment, int minWorkers, int maxWorkers) {
        log.info("Scaling EC2 deployment: {} to {} workers", deployment.getDeploymentId(), minWorkers);

        try {
            String instanceId = ec2CloudProvider.getInstanceIdForTenant(deployment.getTenant().getTenantId());
            if (instanceId == null) {
                throw new DeploymentException("No EC2 instance found for tenant: " + deployment.getTenant().getTenantId());
            }

            String deploymentDir = "/opt/airflow/deployments/" + deployment.getDeploymentId();

            // Scale workers using docker-compose
            List<String> scaleCommands = Arrays.asList(
                    "cd " + deploymentDir,
                    "sudo docker-compose up -d --scale airflow-worker=" + minWorkers
            );

            EC2CommandExecutor.CommandResult result = commandExecutor.executeCommand(instanceId, scaleCommands);

            if (!result.isSuccess()) {
                throw new DeploymentException("Docker Compose scaling failed: " + result.getStderr());
            }

            log.info("Scaled EC2 deployment successfully to {} workers", minWorkers);

        } catch (Exception e) {
            log.error("Failed to scale EC2 deployment: {}", deployment.getDeploymentId(), e);
            throw new DeploymentException("EC2 scaling failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderType() {
        return "ec2";
    }

    /**
     * Get logs from a specific service
     */
    public String getLogs(AirflowDeployment deployment, String serviceName, int tailLines) {
        try {
            String instanceId = ec2CloudProvider.getInstanceIdForTenant(deployment.getTenant().getTenantId());
            if (instanceId == null) {
                return "No instance found";
            }

            String deploymentDir = "/opt/airflow/deployments/" + deployment.getDeploymentId();

            List<String> logCommands = Arrays.asList(
                    "cd " + deploymentDir,
                    "sudo docker-compose logs --tail=" + tailLines + " " + serviceName
            );

            EC2CommandExecutor.CommandResult result = commandExecutor.executeCommand(instanceId, logCommands);
            return result.getStdout();

        } catch (Exception e) {
            log.error("Failed to get logs for deployment: {}", deployment.getDeploymentId(), e);
            return "Error retrieving logs: " + e.getMessage();
        }
    }

    /**
     * Restart a specific service
     */
    public void restartService(AirflowDeployment deployment, String serviceName) {
        try {
            String instanceId = ec2CloudProvider.getInstanceIdForTenant(deployment.getTenant().getTenantId());
            if (instanceId == null) {
                throw new DeploymentException("No EC2 instance found");
            }

            String deploymentDir = "/opt/airflow/deployments/" + deployment.getDeploymentId();

            List<String> restartCommands = Arrays.asList(
                    "cd " + deploymentDir,
                    "sudo docker-compose restart " + serviceName
            );

            commandExecutor.executeCommand(instanceId, restartCommands);
            log.info("Restarted service {} for deployment {}", serviceName, deployment.getDeploymentId());

        } catch (Exception e) {
            log.error("Failed to restart service: {}", serviceName, e);
            throw new DeploymentException("Service restart failed: " + e.getMessage(), e);
        }
    }
}

package com.airflow.platform.provider.impl;

import com.airflow.platform.exception.DeploymentException;
import com.airflow.platform.model.AirflowDeployment;
import com.airflow.platform.provider.DeploymentProvider;
import com.airflow.platform.service.DockerComposeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Local implementation of DeploymentProvider
 * Manages Airflow deployments using Docker Compose on localhost
 */
@Component
@ConditionalOnProperty(name = "deployment.provider", havingValue = "local")
@RequiredArgsConstructor
@Slf4j
public class LocalDeploymentProvider implements DeploymentProvider {

    private final DockerComposeGenerator composeGenerator;

    @Value("${local.base-directory:${user.home}/airflow-deployments}")
    private String baseDirectory;

    @Value("${local.docker-compose-timeout:300}")
    private int timeout;

    @Override
    public void deploy(AirflowDeployment deployment) {
        log.info("Deploying Airflow locally: {}", deployment.getDeploymentId());

        try {
            String deploymentDir = getDeploymentDirectory(deployment);

            // Create deployment directory
            Path deploymentPath = Paths.get(deploymentDir);
            if (!Files.exists(deploymentPath)) {
                Files.createDirectories(deploymentPath);
            }

            // Generate docker-compose.yml
            String dockerCompose = composeGenerator.generateDockerCompose(deployment);
            Path composePath = deploymentPath.resolve("docker-compose.yml");
            Files.writeString(composePath, dockerCompose);

            log.info("Docker Compose file generated: {}", composePath);

            // Run docker-compose up
            executeDockerCompose(deploymentDir, "up", "-d");

            log.info("Airflow deployed successfully: {}", deployment.getDeploymentId());
        } catch (Exception e) {
            log.error("Failed to deploy Airflow locally: {}", deployment.getDeploymentId(), e);
            throw new DeploymentException("Local deployment failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void upgrade(AirflowDeployment deployment) {
        log.info("Upgrading Airflow deployment: {}", deployment.getDeploymentId());

        try {
            String deploymentDir = getDeploymentDirectory(deployment);

            // Regenerate docker-compose.yml with updated configuration
            String dockerCompose = composeGenerator.generateDockerCompose(deployment);
            Path composePath = Paths.get(deploymentDir, "docker-compose.yml");
            Files.writeString(composePath, dockerCompose);

            // Stop and remove existing containers
            executeDockerCompose(deploymentDir, "down");

            // Start with new configuration
            executeDockerCompose(deploymentDir, "up", "-d");

            log.info("Airflow upgraded successfully: {}", deployment.getDeploymentId());
        } catch (Exception e) {
            log.error("Failed to upgrade Airflow: {}", deployment.getDeploymentId(), e);
            throw new DeploymentException("Local upgrade failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void uninstall(AirflowDeployment deployment) {
        log.info("Uninstalling Airflow locally: {}", deployment.getDeploymentId());

        try {
            String deploymentDir = getDeploymentDirectory(deployment);

            if (Files.exists(Paths.get(deploymentDir))) {
                // Stop and remove containers
                executeDockerCompose(deploymentDir, "down", "-v");

                // Delete deployment directory
                deleteDirectory(new File(deploymentDir));

                log.info("Airflow uninstalled successfully: {}", deployment.getDeploymentId());
            } else {
                log.warn("Deployment directory not found: {}", deploymentDir);
            }
        } catch (Exception e) {
            log.error("Failed to uninstall Airflow: {}", deployment.getDeploymentId(), e);
            throw new DeploymentException("Local uninstall failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getDeploymentStatus(AirflowDeployment deployment) {
        try {
            String deploymentDir = getDeploymentDirectory(deployment);

            if (!Files.exists(Paths.get(deploymentDir, "docker-compose.yml"))) {
                return "NOT_FOUND";
            }

            // Check if containers are running
            String output = executeDockerCompose(deploymentDir, "ps", "--services", "--filter", "status=running");

            if (output != null && output.contains("webserver")) {
                return "RUNNING";
            } else if (output != null && !output.trim().isEmpty()) {
                return "STARTING";
            } else {
                return "STOPPED";
            }
        } catch (Exception e) {
            log.error("Failed to get deployment status: {}", deployment.getDeploymentId(), e);
            return "ERROR";
        }
    }

    @Override
    public String getWebserverUrl(AirflowDeployment deployment) {
        // For local deployments, webserver is always on localhost
        int webserverPort = getWebserverPort(deployment);
        return "http://localhost:" + webserverPort;
    }

    @Override
    public void scale(AirflowDeployment deployment, int minWorkers, int maxWorkers) {
        log.info("Scaling deployment {} to {} workers", deployment.getDeploymentId(), minWorkers);

        try {
            String deploymentDir = getDeploymentDirectory(deployment);

            // For local deployment, we'll scale to minWorkers
            // Note: Docker Compose doesn't support max workers natively
            executeDockerCompose(deploymentDir, "up", "-d", "--scale", "worker=" + minWorkers);

            log.info("Scaled successfully to {} workers", minWorkers);
        } catch (Exception e) {
            log.error("Failed to scale deployment: {}", deployment.getDeploymentId(), e);
            throw new DeploymentException("Local scaling failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderType() {
        return "local";
    }

    private String getDeploymentDirectory(AirflowDeployment deployment) {
        return baseDirectory + File.separator +
               deployment.getTenant().getTenantId() + File.separator +
               deployment.getDeploymentId();
    }

    private int getWebserverPort(AirflowDeployment deployment) {
        // Use deployment ID hash to generate unique port
        // Base port: 8080, range: 8080-8180
        int hash = Math.abs(deployment.getDeploymentId().hashCode());
        return 8080 + (hash % 100);
    }

    private String executeDockerCompose(String workingDir, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("docker-compose");
        command.addAll(List.of(args));

        log.info("Executing command in {}: {}", workingDir, String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(workingDir));
        pb.redirectErrorStream(true);

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug(line);
                output.append(line).append("\n");
            }
        }

        boolean completed = process.waitFor(timeout, java.util.concurrent.TimeUnit.SECONDS);

        if (!completed) {
            process.destroyForcibly();
            throw new DeploymentException("Docker Compose command timed out after " + timeout + " seconds");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0 && !args[0].equals("down")) {
            throw new DeploymentException("Docker Compose command failed with exit code: " + exitCode);
        }

        return output.toString();
    }

    private void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }
}

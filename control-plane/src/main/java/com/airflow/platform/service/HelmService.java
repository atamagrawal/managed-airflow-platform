package com.airflow.platform.service;

import com.airflow.platform.exception.DeploymentException;
import com.airflow.platform.model.AirflowDeployment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing Helm chart deployments
 */
@Service
@Slf4j
public class HelmService {

    @Value("${helm.chart.path:../helm-charts/airflow-deployment}")
    private String helmChartPath;

    @Value("${helm.repo.name:apache-airflow}")
    private String helmRepoName;

    @Value("${helm.repo.url:https://airflow.apache.org}")
    private String helmRepoUrl;

    /**
     * Deploy Airflow using Helm
     */
    public void deployAirflow(AirflowDeployment deployment) {
        log.info("Deploying Airflow via Helm: {}", deployment.getHelmReleaseName());

        List<String> command = buildHelmInstallCommand(deployment);

        try {
            executeCommand(command);
            log.info("Airflow deployed successfully via Helm: {}", deployment.getHelmReleaseName());
        } catch (Exception e) {
            log.error("Failed to deploy Airflow via Helm: {}", deployment.getHelmReleaseName(), e);
            throw new DeploymentException("Helm deployment failed: " + e.getMessage(), e);
        }
    }

    /**
     * Upgrade Airflow deployment using Helm
     */
    public void upgradeAirflow(AirflowDeployment deployment) {
        log.info("Upgrading Airflow via Helm: {}", deployment.getHelmReleaseName());

        List<String> command = buildHelmUpgradeCommand(deployment);

        try {
            executeCommand(command);
            log.info("Airflow upgraded successfully via Helm: {}", deployment.getHelmReleaseName());
        } catch (Exception e) {
            log.error("Failed to upgrade Airflow via Helm: {}", deployment.getHelmReleaseName(), e);
            throw new DeploymentException("Helm upgrade failed: " + e.getMessage(), e);
        }
    }

    /**
     * Uninstall Airflow deployment using Helm
     */
    public void uninstallAirflow(AirflowDeployment deployment) {
        log.info("Uninstalling Airflow via Helm: {}", deployment.getHelmReleaseName());

        List<String> command = new ArrayList<>();
        command.add("helm");
        command.add("uninstall");
        command.add(deployment.getHelmReleaseName());
        command.add("-n");
        command.add(deployment.getNamespace());

        try {
            executeCommand(command);
            log.info("Airflow uninstalled successfully via Helm: {}", deployment.getHelmReleaseName());
        } catch (Exception e) {
            log.error("Failed to uninstall Airflow via Helm: {}", deployment.getHelmReleaseName(), e);
            throw new DeploymentException("Helm uninstall failed: " + e.getMessage(), e);
        }
    }

    private List<String> buildHelmInstallCommand(AirflowDeployment deployment) {
        List<String> command = new ArrayList<>();
        command.add("helm");
        command.add("install");
        command.add(deployment.getHelmReleaseName());
        command.add(helmRepoName + "/airflow");
        command.add("-n");
        command.add(deployment.getNamespace());
        command.add("--version");
        command.add(deployment.getAirflowVersion());
        command.add("--create-namespace");

        // Add configuration values
        addHelmValues(command, deployment);

        return command;
    }

    private List<String> buildHelmUpgradeCommand(AirflowDeployment deployment) {
        List<String> command = new ArrayList<>();
        command.add("helm");
        command.add("upgrade");
        command.add(deployment.getHelmReleaseName());
        command.add(helmRepoName + "/airflow");
        command.add("-n");
        command.add(deployment.getNamespace());
        command.add("--version");
        command.add(deployment.getAirflowVersion());

        // Add configuration values
        addHelmValues(command, deployment);

        return command;
    }

    private void addHelmValues(List<String> command, AirflowDeployment deployment) {
        // Executor type
        command.add("--set");
        command.add("executor=" + deployment.getExecutorType().name());

        // Scheduler resources
        command.add("--set");
        command.add("scheduler.resources.requests.cpu=" + deployment.getSchedulerCpu());
        command.add("--set");
        command.add("scheduler.resources.requests.memory=" + deployment.getSchedulerMemory());

        // Worker resources
        command.add("--set");
        command.add("workers.resources.requests.cpu=" + deployment.getWorkerCpu());
        command.add("--set");
        command.add("workers.resources.requests.memory=" + deployment.getWorkerMemory());

        // Webserver resources
        command.add("--set");
        command.add("webserver.resources.requests.cpu=" + deployment.getWebserverCpu());
        command.add("--set");
        command.add("webserver.resources.requests.memory=" + deployment.getWebserverMemory());

        // Worker autoscaling (for KEDA)
        if (deployment.getExecutorType() == AirflowDeployment.ExecutorType.CELERY ||
                deployment.getExecutorType() == AirflowDeployment.ExecutorType.CELERY_KUBERNETES) {
            command.add("--set");
            command.add("workers.keda.enabled=true");
            command.add("--set");
            command.add("workers.keda.minReplicaCount=" + deployment.getMinWorkers());
            command.add("--set");
            command.add("workers.keda.maxReplicaCount=" + deployment.getMaxWorkers());
        }

        // Ingress configuration
        if (deployment.getIngressHost() != null && !deployment.getIngressHost().isEmpty()) {
            command.add("--set");
            command.add("ingress.enabled=true");
            command.add("--set");
            command.add("ingress.web.host=" + deployment.getIngressHost());
        }
    }

    private void executeCommand(List<String> command) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        log.debug("Executing command: {}", String.join(" ", command));

        Process process = processBuilder.start();

        // Read output
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                log.debug(line);
            }
        }

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new DeploymentException("Command failed with exit code " + exitCode + ": " + output.toString());
        }
    }
}

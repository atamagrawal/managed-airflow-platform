package com.airflow.platform.provider.impl;

import com.airflow.platform.exception.DeploymentException;
import com.airflow.platform.model.Tenant;
import com.airflow.platform.provider.CloudProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Local implementation of CloudProvider
 * Manages local directories for tenant isolation
 */
@Component
@ConditionalOnProperty(name = "deployment.provider", havingValue = "local")
@RequiredArgsConstructor
@Slf4j
public class LocalCloudProvider implements CloudProvider {

    @Value("${local.base-directory:${user.home}/airflow-deployments}")
    private String baseDirectory;

    @Override
    public void createNamespace(Tenant tenant) {
        String tenantDir = getTenantDirectory(tenant);
        log.info("Creating local directory for tenant: {}", tenantDir);

        try {
            Path path = Paths.get(tenantDir);
            if (!Files.exists(path)) {
                Files.createDirectories(path);

                // Create subdirectories
                Files.createDirectories(path.resolve("dags"));
                Files.createDirectories(path.resolve("logs"));
                Files.createDirectories(path.resolve("plugins"));
                Files.createDirectories(path.resolve("data"));

                log.info("Local directory created successfully: {}", tenantDir);
            } else {
                log.info("Local directory already exists: {}", tenantDir);
            }
        } catch (IOException e) {
            log.error("Failed to create local directory: {}", tenantDir, e);
            throw new DeploymentException("Failed to create local directory: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteNamespace(Tenant tenant) {
        String tenantDir = getTenantDirectory(tenant);
        log.info("Deleting local directory: {}", tenantDir);

        try {
            Path path = Paths.get(tenantDir);
            if (Files.exists(path)) {
                deleteDirectory(path.toFile());
                log.info("Local directory deleted successfully: {}", tenantDir);
            } else {
                log.info("Local directory does not exist: {}", tenantDir);
            }
        } catch (Exception e) {
            log.error("Failed to delete local directory: {}", tenantDir, e);
            throw new DeploymentException("Failed to delete local directory: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean namespaceExists(String namespace) {
        String tenantDir = baseDirectory + File.separator + namespace;
        return Files.exists(Paths.get(tenantDir));
    }

    @Override
    public void createSecret(String namespace, String secretName, Map<String, String> data) {
        log.info("Creating secret {} for tenant {}", secretName, namespace);

        try {
            String tenantDir = baseDirectory + File.separator + namespace;
            Path secretsDir = Paths.get(tenantDir, "secrets");

            if (!Files.exists(secretsDir)) {
                Files.createDirectories(secretsDir);
            }

            Path secretFile = secretsDir.resolve(secretName + ".properties");

            StringBuilder content = new StringBuilder();
            data.forEach((key, value) ->
                content.append(key).append("=").append(value).append("\n")
            );

            Files.writeString(secretFile, content.toString());
            log.info("Secret created successfully: {}", secretFile);
        } catch (IOException e) {
            log.error("Failed to create secret: {}", secretName, e);
            throw new DeploymentException("Failed to create secret: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, String> getCloudSpecificConfig(Tenant tenant) {
        Map<String, String> config = new HashMap<>();
        config.put("directory", getTenantDirectory(tenant));
        config.put("provider", "local");
        return config;
    }

    @Override
    public String getProviderType() {
        return "local";
    }

    private String getTenantDirectory(Tenant tenant) {
        return baseDirectory + File.separator + tenant.getTenantId();
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

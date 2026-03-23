package com.airflow.platform.service;

import com.airflow.platform.exception.DeploymentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Executes commands on EC2 instances using AWS Systems Manager
 */
@Service
@ConditionalOnProperty(name = "deployment.provider", havingValue = "ec2")
@RequiredArgsConstructor
@Slf4j
public class EC2CommandExecutor {

    private final SsmClient ssmClient;

    @Value("${aws.ec2.command-timeout:300}")
    private int commandTimeout;

    /**
     * Execute a shell command on an EC2 instance
     */
    public CommandResult executeCommand(String instanceId, String command) {
        return executeCommand(instanceId, List.of(command));
    }

    /**
     * Execute multiple shell commands on an EC2 instance
     */
    public CommandResult executeCommand(String instanceId, List<String> commands) {
        log.info("Executing command on instance {}: {}", instanceId, commands);

        try {
            // Send command via SSM
            SendCommandRequest request = SendCommandRequest.builder()
                    .instanceIds(instanceId)
                    .documentName("AWS-RunShellScript")
                    .parameters(Map.of("commands", commands))
                    .timeoutSeconds(commandTimeout)
                    .build();

            SendCommandResponse response = ssmClient.sendCommand(request);
            String commandId = response.command().commandId();

            // Wait for command to complete
            return waitForCommandCompletion(commandId, instanceId);

        } catch (Exception e) {
            log.error("Failed to execute command on instance {}", instanceId, e);
            throw new DeploymentException("Command execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute a command asynchronously (don't wait for completion)
     */
    public String executeCommandAsync(String instanceId, List<String> commands) {
        log.info("Executing command asynchronously on instance {}: {}", instanceId, commands);

        try {
            SendCommandRequest request = SendCommandRequest.builder()
                    .instanceIds(instanceId)
                    .documentName("AWS-RunShellScript")
                    .parameters(Map.of("commands", commands))
                    .timeoutSeconds(commandTimeout)
                    .build();

            SendCommandResponse response = ssmClient.sendCommand(request);
            return response.command().commandId();

        } catch (Exception e) {
            log.error("Failed to execute command on instance {}", instanceId, e);
            throw new DeploymentException("Command execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Check the status of a command
     */
    public CommandResult getCommandResult(String commandId, String instanceId) {
        try {
            GetCommandInvocationRequest request = GetCommandInvocationRequest.builder()
                    .commandId(commandId)
                    .instanceId(instanceId)
                    .build();

            GetCommandInvocationResponse response = ssmClient.getCommandInvocation(request);

            return CommandResult.builder()
                    .commandId(commandId)
                    .status(response.statusAsString())
                    .exitCode(response.responseCode())
                    .stdout(response.standardOutputContent())
                    .stderr(response.standardErrorContent())
                    .build();

        } catch (Exception e) {
            log.error("Failed to get command result: {}", commandId, e);
            throw new DeploymentException("Failed to get command result: " + e.getMessage(), e);
        }
    }

    /**
     * Copy a file to an EC2 instance
     */
    public void copyFileToInstance(String instanceId, String localContent, String remotePath) {
        log.info("Copying file to instance {}: {}", instanceId, remotePath);

        // Escape single quotes in content
        String escapedContent = localContent.replace("'", "'\\''");

        List<String> commands = List.of(
                "mkdir -p $(dirname " + remotePath + ")",
                "cat > " + remotePath + " << 'EOF'\n" + localContent + "\nEOF",
                "chmod 644 " + remotePath
        );

        executeCommand(instanceId, commands);
    }

    private CommandResult waitForCommandCompletion(String commandId, String instanceId) {
        int maxAttempts = 60; // 5 minutes max wait (5 second intervals)
        int attempt = 0;

        while (attempt < maxAttempts) {
            try {
                CommandResult result = getCommandResult(commandId, instanceId);

                if ("Success".equals(result.getStatus()) ||
                    "Failed".equals(result.getStatus()) ||
                    "Cancelled".equals(result.getStatus()) ||
                    "TimedOut".equals(result.getStatus())) {

                    log.info("Command completed with status: {}", result.getStatus());
                    return result;
                }

                // Still in progress
                TimeUnit.SECONDS.sleep(5);
                attempt++;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DeploymentException("Command execution interrupted", e);
            }
        }

        throw new DeploymentException("Command execution timed out after " + (maxAttempts * 5) + " seconds");
    }

    @lombok.Data
    @lombok.Builder
    public static class CommandResult {
        private String commandId;
        private String status;
        private Integer exitCode;
        private String stdout;
        private String stderr;

        public boolean isSuccess() {
            return "Success".equals(status) && (exitCode == null || exitCode == 0);
        }
    }
}

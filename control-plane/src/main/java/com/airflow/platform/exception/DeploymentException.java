package com.airflow.platform.exception;

/**
 * Exception thrown when a deployment operation fails
 */
public class DeploymentException extends RuntimeException {

    public DeploymentException(String message) {
        super(message);
    }

    public DeploymentException(String message, Throwable cause) {
        super(message, cause);
    }
}

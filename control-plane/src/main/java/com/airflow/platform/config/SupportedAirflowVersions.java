package com.airflow.platform.config;

import com.airflow.platform.exception.DeploymentException;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Airflow releases the control plane is tested against. Expand as new versions are validated.
 */
public final class SupportedAirflowVersions {

    private static final Set<String> ALLOWED = Set.of("3.1.8");

    private SupportedAirflowVersions() {
    }

    public static void requireSupported(String airflowVersion) {
        if (airflowVersion == null || airflowVersion.isBlank()) {
            return;
        }
        String v = airflowVersion.trim();
        if (!ALLOWED.contains(v)) {
            throw new DeploymentException(
                    "Unsupported Airflow version: '%s'. Supported: %s"
                            .formatted(v, ALLOWED.stream().sorted().collect(Collectors.joining(", "))));
        }
    }
}

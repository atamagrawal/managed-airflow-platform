package com.airflow.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * Syncs one Airflow connection to one or more deployments ({@code syncScope}).
 * {@value #SYNC_SCOPE_SELECTED} matches an Astro-style “link to deployments” workflow.
 */
@Data
public class EnvironmentConnectionSyncRequest {

    public static final String SYNC_SCOPE_ALL = "ALL";
    public static final String SYNC_SCOPE_SELECTED = "SELECTED";
    /** @deprecated use {@link #SYNC_SCOPE_SELECTED} with a single id in {@link #targetDeploymentIds}. */
    public static final String SYNC_SCOPE_SPECIFIC = "SPECIFIC";

    /**
     * {@value #SYNC_SCOPE_ALL}, {@value #SYNC_SCOPE_SELECTED}, or legacy {@value #SYNC_SCOPE_SPECIFIC}.
     */
    @NotBlank
    private String syncScope;

    /**
     * Required when {@code syncScope} is {@value #SYNC_SCOPE_SELECTED}: non-empty list of platform {@code deploymentId}s.
     */
    private List<String> targetDeploymentIds;

    /**
     * Legacy: required when {@code syncScope} is {@value #SYNC_SCOPE_SPECIFIC}.
     */
    private String targetDeploymentId;

    @NotBlank
    private String connectionId;

    @NotBlank
    private String connType;

    private String description;
    private String host;
    private String login;
    private String password;
    private Integer port;
    private String schema;
    /** JSON object string passed to Airflow {@code extra}. */
    private String extra;
}

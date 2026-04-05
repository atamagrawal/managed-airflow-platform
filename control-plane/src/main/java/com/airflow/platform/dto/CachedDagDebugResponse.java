package com.airflow.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CachedDagDebugResponse {

    private String deploymentId;
    private String deploymentName;
    private String dagId;
    private boolean paused;
    private Boolean active;
    private String fileloc;
    private String owners;
    private String description;
    private boolean importError;
    private String importErrorStackTrace;
    private Instant syncedAt;
}

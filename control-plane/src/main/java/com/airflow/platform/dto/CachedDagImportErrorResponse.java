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
public class CachedDagImportErrorResponse {

    private String deploymentId;
    private String deploymentName;
    private String filename;
    private String stackTrace;
    private Instant sourceTimestamp;
    private Instant syncedAt;
}

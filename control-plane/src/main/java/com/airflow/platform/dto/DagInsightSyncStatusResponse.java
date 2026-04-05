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
public class DagInsightSyncStatusResponse {

    private String deploymentId;
    private String deploymentName;
    private Instant lastSyncStartedAt;
    private Instant lastSyncCompletedAt;
    private Boolean lastSyncSuccess;
    private String lastErrorMessage;
}

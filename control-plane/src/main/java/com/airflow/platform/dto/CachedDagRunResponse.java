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
public class CachedDagRunResponse {

    private String deploymentId;
    private String deploymentName;
    private String dagId;
    private String dagRunId;
    private String state;
    private Instant logicalDate;
    private Instant startDate;
    private Instant endDate;
    private String runType;
    private Instant syncedAt;
}

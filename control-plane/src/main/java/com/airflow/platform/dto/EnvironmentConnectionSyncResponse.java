package com.airflow.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnvironmentConnectionSyncResponse {
    private boolean allSucceeded;
    private List<EnvironmentConnectionTargetResult> results;
}

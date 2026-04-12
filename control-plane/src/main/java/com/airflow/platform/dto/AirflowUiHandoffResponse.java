package com.airflow.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AirflowUiHandoffResponse {
    /** Single-use id for {@code GET /api/v1/public/airflow-handoff/{handoffId}}. */
    private String handoffId;
}

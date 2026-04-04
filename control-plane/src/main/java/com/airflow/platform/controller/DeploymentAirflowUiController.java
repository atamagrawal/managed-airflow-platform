package com.airflow.platform.controller;

import com.airflow.platform.dto.AirflowUiHandoffResponse;
import com.airflow.platform.service.AirflowUiHandoffService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/deployments")
@RequiredArgsConstructor
public class DeploymentAirflowUiController {

    private final AirflowUiHandoffService airflowUiHandoffService;

    /**
     * Issues a single-use id; open {@code GET /api/v1/public/airflow-handoff/{handoffId}} in a new tab to complete
     * Airflow 3 FAB browser login (password resolved from the signed-in platform user; no request body).
     */
    @PostMapping("/{deploymentId}/airflow-ui-handoff")
    public AirflowUiHandoffResponse createAirflowUiHandoff(@PathVariable String deploymentId) {
        String id = airflowUiHandoffService.createTicket(deploymentId);
        return AirflowUiHandoffResponse.builder().handoffId(id).build();
    }
}

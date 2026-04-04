package com.airflow.platform.controller;

import com.airflow.platform.service.AirflowUiHandoffService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/airflow-handoff")
@RequiredArgsConstructor
public class PublicAirflowHandoffController {

    private final AirflowUiHandoffService airflowUiHandoffService;

    @GetMapping(value = "/{handoffId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> handoffPage(@PathVariable String handoffId) {
        String html = airflowUiHandoffService.buildHandoffPageHtml(handoffId);
        return ResponseEntity.ok(html);
    }

    /**
     * Same-origin completion: returns a one-time JSON payload; the handoff page form-POSTs to Airflow
     * {@code /auth/managed-platform-ui-handoff}
     * so session cookies are set on the Airflow origin (required for the FAB UI).
     */
    @PostMapping(value = "/{handoffId}/complete", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> completeHandoff(@PathVariable String handoffId) {
        return airflowUiHandoffService.completeHandoff(handoffId);
    }
}

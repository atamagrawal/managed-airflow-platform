package com.airflow.platform.controller;

import com.airflow.platform.dto.EnvironmentConnectionSyncRequest;
import com.airflow.platform.dto.EnvironmentConnectionSyncResponse;
import com.airflow.platform.service.EnvironmentConnectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/environment/connections")
@RequiredArgsConstructor
public class EnvironmentConnectionController {

    private final EnvironmentConnectionService environmentConnectionService;

    @PostMapping("/sync")
    public EnvironmentConnectionSyncResponse sync(@Valid @RequestBody EnvironmentConnectionSyncRequest request) {
        return environmentConnectionService.syncConnection(request);
    }
}

package com.airflow.platform.controller;

import com.airflow.platform.dto.UserAccountResponse;
import com.airflow.platform.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin users", description = "Configured control-plane accounts (admin only)")
public class AdminUserController {

    private final AuthService authService;

    @GetMapping
    @Operation(summary = "List configured users (no passwords)")
    public ResponseEntity<List<UserAccountResponse>> listUsers() {
        return ResponseEntity.ok(authService.listConfiguredUsers());
    }
}

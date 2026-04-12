package com.airflow.platform.controller;

import com.airflow.platform.dto.PlatformUserCreateRequest;
import com.airflow.platform.dto.UserAccountResponse;
import com.airflow.platform.service.PlatformUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin users", description = "Control-plane accounts (admin only)")
public class AdminUserController {

    private final PlatformUserService platformUserService;

    @GetMapping
    @Operation(summary = "List users (database + configuration); no passwords")
    public ResponseEntity<List<UserAccountResponse>> listUsers() {
        return ResponseEntity.ok(platformUserService.listMergedWithConfiguration());
    }

    @PostMapping
    @Operation(summary = "Create a database-backed user")
    public ResponseEntity<UserAccountResponse> createUser(@Valid @RequestBody PlatformUserCreateRequest request) {
        return ResponseEntity.ok(platformUserService.createUser(request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a database-backed user (configuration users cannot be removed here)")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        platformUserService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}

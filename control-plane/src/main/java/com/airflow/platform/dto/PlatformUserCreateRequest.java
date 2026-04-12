package com.airflow.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PlatformUserCreateRequest {

    @NotBlank(message = "Username is required")
    @Size(max = 100, message = "Username must not exceed 100 characters")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 200, message = "Password must be between 8 and 200 characters")
    private String password;

    /**
     * At least one role; use ADMIN and/or USER (uppercase recommended).
     */
    private List<String> roles;

    /**
     * Optional home tenant for non-admin users; if omitted, platform default applies.
     */
    @Size(max = 100, message = "Tenant id must not exceed 100 characters")
    private String tenantId;
}

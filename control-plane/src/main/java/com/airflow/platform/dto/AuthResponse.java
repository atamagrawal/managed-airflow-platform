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
public class AuthResponse {

    private String accessToken;
    private String tokenType;
    private long expiresInMs;
    private String username;
    private List<String> roles;
    /** Non-null for non-admin users; deployments are limited to this tenant. */
    private String tenantScope;
    private boolean admin;
}

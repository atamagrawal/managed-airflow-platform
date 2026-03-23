package com.airflow.platform.dto;

import com.airflow.platform.model.Tenant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for tenant response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantResponse {

    private Long id;
    private String tenantId;
    private String name;
    private String email;
    private String organization;
    private String status;
    private String kubernetesNamespace;
    private String cloudProvider;
    private String clusterName;
    private String region;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TenantResponse fromEntity(Tenant tenant) {
        return TenantResponse.builder()
                .id(tenant.getId())
                .tenantId(tenant.getTenantId())
                .name(tenant.getName())
                .email(tenant.getEmail())
                .organization(tenant.getOrganization())
                .status(tenant.getStatus().name())
                .kubernetesNamespace(tenant.getKubernetesNamespace())
                .cloudProvider(tenant.getCloudProvider())
                .clusterName(tenant.getClusterName())
                .region(tenant.getRegion())
                .createdAt(tenant.getCreatedAt())
                .updatedAt(tenant.getUpdatedAt())
                .build();
    }
}

package com.airflow.platform.service;

import com.airflow.platform.dto.EnvironmentConnectionSyncRequest;
import com.airflow.platform.dto.EnvironmentConnectionSyncResponse;
import com.airflow.platform.dto.EnvironmentConnectionTargetResult;
import com.airflow.platform.exception.DeploymentException;
import com.airflow.platform.exception.ResourceNotFoundException;
import com.airflow.platform.model.AirflowDeployment;
import com.airflow.platform.repository.AirflowDeploymentRepository;
import com.airflow.platform.security.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnvironmentConnectionService {

    private final AirflowDeploymentRepository deploymentRepository;
    private final AirflowRemoteApiService airflowRemoteApiService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public EnvironmentConnectionSyncResponse syncConnection(EnvironmentConnectionSyncRequest request) {
        validateRequest(request);

        List<AirflowDeployment> targets = resolveTargets(request);
        if (targets.isEmpty()) {
            throw new IllegalArgumentException(
                    EnvironmentConnectionSyncRequest.SYNC_SCOPE_ALL.equalsIgnoreCase(request.getSyncScope())
                            ? "No running deployments with a webserver URL are available for your account."
                            : "No target deployments resolved for sync.");
        }

        String connId = request.getConnectionId().trim();
        List<EnvironmentConnectionTargetResult> results = new ArrayList<>();

        for (AirflowDeployment deployment : targets) {
            EnvironmentConnectionTargetResult result = syncToDeployment(deployment, request, connId);
            results.add(result);
        }

        boolean allOk = results.stream().allMatch(EnvironmentConnectionTargetResult::isSuccess);
        return EnvironmentConnectionSyncResponse.builder()
                .allSucceeded(allOk)
                .results(results)
                .build();
    }

    private void validateRequest(EnvironmentConnectionSyncRequest request) {
        String scope = request.getSyncScope();
        if (scope == null || scope.isBlank()) {
            throw new IllegalArgumentException("syncScope is required (ALL, SELECTED, or SPECIFIC).");
        }
        if (EnvironmentConnectionSyncRequest.SYNC_SCOPE_SELECTED.equalsIgnoreCase(scope)) {
            if (request.getTargetDeploymentIds() == null || request.getTargetDeploymentIds().isEmpty()) {
                throw new IllegalArgumentException(
                        "targetDeploymentIds must contain at least one deployment when syncScope is SELECTED.");
            }
        } else if (EnvironmentConnectionSyncRequest.SYNC_SCOPE_SPECIFIC.equalsIgnoreCase(scope)) {
            if (!StringUtils.hasText(request.getTargetDeploymentId())
                    && (request.getTargetDeploymentIds() == null || request.getTargetDeploymentIds().isEmpty())) {
                throw new IllegalArgumentException(
                        "targetDeploymentId or targetDeploymentIds is required when syncScope is SPECIFIC.");
            }
        } else if (!EnvironmentConnectionSyncRequest.SYNC_SCOPE_ALL.equalsIgnoreCase(scope)) {
            throw new IllegalArgumentException("syncScope must be ALL, SELECTED, or SPECIFIC.");
        }
        if (StringUtils.hasText(request.getExtra())) {
            try {
                objectMapper.readTree(request.getExtra().trim());
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("extra must be valid JSON: " + e.getOriginalMessage());
            }
        }
    }

    private List<AirflowDeployment> resolveTargets(EnvironmentConnectionSyncRequest request) {
        if (EnvironmentConnectionSyncRequest.SYNC_SCOPE_SELECTED.equalsIgnoreCase(request.getSyncScope())) {
            return resolveSelectedDeployments(request.getTargetDeploymentIds());
        }

        if (EnvironmentConnectionSyncRequest.SYNC_SCOPE_SPECIFIC.equalsIgnoreCase(request.getSyncScope())) {
            if (StringUtils.hasText(request.getTargetDeploymentId())) {
                String id = request.getTargetDeploymentId().trim();
                AirflowDeployment d = deploymentRepository.findByDeploymentId(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + id));
                SecurityUtils.assertTenantInScope(d.getTenant().getTenantId());
                return List.of(d);
            }
            return resolveSelectedDeployments(request.getTargetDeploymentIds());
        }

        List<AirflowDeployment> all = listDeploymentsForCaller();
        return all.stream()
                .filter(d -> d.getStatus() == AirflowDeployment.DeploymentStatus.RUNNING)
                .filter(d -> StringUtils.hasText(d.getWebserverUrl()))
                .sorted(Comparator.comparing(AirflowDeployment::getDeploymentId))
                .collect(Collectors.toList());
    }

    private List<AirflowDeployment> resolveSelectedDeployments(List<String> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String id : rawIds) {
            if (id != null && StringUtils.hasText(id.trim())) {
                unique.add(id.trim());
            }
        }
        List<AirflowDeployment> out = new ArrayList<>();
        for (String deploymentId : unique) {
            AirflowDeployment d = deploymentRepository.findByDeploymentId(deploymentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + deploymentId));
            SecurityUtils.assertTenantInScope(d.getTenant().getTenantId());
            out.add(d);
        }
        out.sort(Comparator.comparing(AirflowDeployment::getDeploymentId));
        return out;
    }

    private List<AirflowDeployment> listDeploymentsForCaller() {
        if (SecurityUtils.isAdmin()) {
            return deploymentRepository.findAll();
        }
        String tenantId = SecurityUtils.getNonAdminTenantScope()
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Not authorized"));
        return deploymentRepository.findByTenantTenantId(tenantId);
    }

    private EnvironmentConnectionTargetResult syncToDeployment(AirflowDeployment deployment,
                                                                 EnvironmentConnectionSyncRequest request,
                                                                 String connectionId) {
        String deploymentId = deployment.getDeploymentId();
        String name = deployment.getName();
        String tenantId = deployment.getTenant().getTenantId();

        if (!StringUtils.hasText(deployment.getWebserverUrl())) {
            return EnvironmentConnectionTargetResult.builder()
                    .deploymentId(deploymentId)
                    .deploymentName(name)
                    .tenantId(tenantId)
                    .success(false)
                    .message("Deployment has no webserver URL; cannot call Airflow API.")
                    .build();
        }

        try {
            String extra = StringUtils.hasText(request.getExtra()) ? request.getExtra().trim() : null;
            airflowRemoteApiService.upsertConnection(
                    deployment.getWebserverUrl(),
                    deployment.getAirflowVersion(),
                    connectionId,
                    request.getConnType().trim(),
                    request.getDescription() != null ? request.getDescription().trim() : null,
                    request.getHost() != null ? request.getHost().trim() : null,
                    request.getLogin() != null ? request.getLogin().trim() : null,
                    request.getPassword(),
                    request.getPort(),
                    request.getSchema() != null ? request.getSchema().trim() : null,
                    extra
            );
            return EnvironmentConnectionTargetResult.builder()
                    .deploymentId(deploymentId)
                    .deploymentName(name)
                    .tenantId(tenantId)
                    .success(true)
                    .message("Connection synced.")
                    .build();
        } catch (DeploymentException e) {
            log.warn("Connection sync failed for deployment {}: {}", deploymentId, e.getMessage());
            return EnvironmentConnectionTargetResult.builder()
                    .deploymentId(deploymentId)
                    .deploymentName(name)
                    .tenantId(tenantId)
                    .success(false)
                    .message(e.getMessage())
                    .build();
        } catch (Exception e) {
            log.warn("Connection sync failed for deployment {}: {}", deploymentId, e.getMessage());
            return EnvironmentConnectionTargetResult.builder()
                    .deploymentId(deploymentId)
                    .deploymentName(name)
                    .tenantId(tenantId)
                    .success(false)
                    .message(e.getMessage() != null ? e.getMessage() : "Unknown error")
                    .build();
        }
    }
}

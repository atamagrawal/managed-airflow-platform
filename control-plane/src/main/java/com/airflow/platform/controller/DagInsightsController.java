package com.airflow.platform.controller;

import com.airflow.platform.dto.CachedDagDebugResponse;
import com.airflow.platform.dto.CachedDagImportErrorResponse;
import com.airflow.platform.dto.CachedDagRunResponse;
import com.airflow.platform.dto.DagInsightSyncStatusResponse;
import com.airflow.platform.dto.PageResponse;
import com.airflow.platform.exception.ResourceNotFoundException;
import com.airflow.platform.model.AirflowDeployment;
import com.airflow.platform.repository.AirflowDeploymentRepository;
import com.airflow.platform.security.SecurityUtils;
import com.airflow.platform.service.DagInsightsQueryService;
import com.airflow.platform.service.DagInsightsSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dag-insights")
@RequiredArgsConstructor
@Tag(name = "DAG insights", description = "Cached DAG runs and debug metadata from Airflow")
public class DagInsightsController {

    private final DagInsightsQueryService dagInsightsQueryService;
    private final DagInsightsSyncService dagInsightsSyncService;
    private final AirflowDeploymentRepository deploymentRepository;

    @GetMapping("/runs")
    @Operation(summary = "List cached DAG runs (paginated)")
    public PageResponse<CachedDagRunResponse> listRuns(
            @RequestParam(required = false) String deploymentId,
            @RequestParam(required = false) String dagId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return dagInsightsQueryService.listCachedRuns(deploymentId, dagId, page, size);
    }

    @GetMapping("/debug")
    @Operation(summary = "List cached DAG metadata for debug (pause/active/import linkage)")
    public PageResponse<CachedDagDebugResponse> listDebug(
            @RequestParam(required = false) String deploymentId,
            @RequestParam(required = false) Boolean errorsOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return dagInsightsQueryService.listCachedDebug(deploymentId, errorsOnly, page, size);
    }

    @GetMapping("/import-errors")
    @Operation(summary = "List raw import errors from Airflow (cached)")
    public PageResponse<CachedDagImportErrorResponse> listImportErrors(
            @RequestParam(required = false) String deploymentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return dagInsightsQueryService.listCachedImportErrors(deploymentId, page, size);
    }

    @GetMapping("/sync-status")
    @Operation(summary = "Last sync status per deployment in scope")
    public List<DagInsightSyncStatusResponse> syncStatus(@RequestParam(required = false) String deploymentId) {
        return dagInsightsQueryService.listSyncStatuses(deploymentId);
    }

    @PostMapping("/sync")
    @Operation(summary = "Queue a refresh of cached DAG insights from Airflow (202 Accepted)")
    public ResponseEntity<Void> triggerSync(@RequestParam(required = false) String deploymentId) {
        if (StringUtils.hasText(deploymentId)) {
            String id = deploymentId.trim();
            AirflowDeployment d = deploymentRepository.findByDeploymentId(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + id));
            SecurityUtils.assertTenantInScope(d.getTenant().getTenantId());
            dagInsightsSyncService.triggerSyncDeployment(id);
        } else if (SecurityUtils.isAdmin()) {
            dagInsightsSyncService.triggerSyncAllRunnableDeployments();
        } else {
            String tenant = SecurityUtils.getNonAdminTenantScope()
                    .orElseThrow(() -> new AccessDeniedException("Not authorized"));
            dagInsightsSyncService.triggerSyncDeploymentsForTenant(tenant);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}

package com.airflow.platform.service;

import com.airflow.platform.dto.CachedDagDebugResponse;
import com.airflow.platform.dto.CachedDagImportErrorResponse;
import com.airflow.platform.dto.CachedDagRunResponse;
import com.airflow.platform.dto.DagInsightSyncStatusResponse;
import com.airflow.platform.dto.PageResponse;
import com.airflow.platform.exception.ResourceNotFoundException;
import com.airflow.platform.model.AirflowDeployment;
import com.airflow.platform.model.CachedDagImportError;
import com.airflow.platform.model.CachedDagMeta;
import com.airflow.platform.model.CachedDagRun;
import com.airflow.platform.repository.AirflowDeploymentRepository;
import com.airflow.platform.repository.CachedDagImportErrorRepository;
import com.airflow.platform.repository.CachedDagMetaRepository;
import com.airflow.platform.repository.CachedDagRunRepository;
import com.airflow.platform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DagInsightsQueryService {

    private final AirflowDeploymentRepository deploymentRepository;
    private final CachedDagRunRepository cachedDagRunRepository;
    private final CachedDagMetaRepository cachedDagMetaRepository;
    private final CachedDagImportErrorRepository cachedDagImportErrorRepository;
    private final DagInsightsSyncService dagInsightsSyncService;

    @Transactional(readOnly = true)
    public PageResponse<CachedDagRunResponse> listCachedRuns(
            String deploymentIdFilter, String dagIdFilter, int page, int size) {
        List<String> scope = resolveDeploymentScope(deploymentIdFilter);
        if (scope.isEmpty()) {
            return emptyPage(page, size);
        }
        Sort sort = Sort.by(new Sort.Order(Sort.Direction.DESC, "startDate").nullsLast());
        PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 200), sort);
        Page<CachedDagRun> result;
        String dagTrim = StringUtils.hasText(dagIdFilter) ? dagIdFilter.trim() : null;
        if (scope.size() == 1) {
            String dep = scope.get(0);
            if (dagTrim != null) {
                result = cachedDagRunRepository.findAllByDeploymentIdAndDagId(dep, dagTrim, pr);
            } else {
                result = cachedDagRunRepository.findAllByDeploymentId(dep, pr);
            }
        } else {
            if (dagTrim != null) {
                result = cachedDagRunRepository.findAllByDeploymentIdInAndDagId(scope, dagTrim, pr);
            } else {
                result = cachedDagRunRepository.findAllByDeploymentIdIn(scope, pr);
            }
        }
        Map<String, String> names = deploymentNames(result.getContent().stream()
                .map(CachedDagRun::getDeploymentId)
                .collect(Collectors.toSet()));
        List<CachedDagRunResponse> mapped = result.getContent().stream()
                .map(r -> CachedDagRunResponse.builder()
                        .deploymentId(r.getDeploymentId())
                        .deploymentName(names.getOrDefault(r.getDeploymentId(), r.getDeploymentId()))
                        .dagId(r.getDagId())
                        .dagRunId(r.getDagRunId())
                        .state(r.getState())
                        .logicalDate(r.getLogicalDate())
                        .startDate(r.getStartDate())
                        .endDate(r.getEndDate())
                        .runType(r.getRunType())
                        .syncedAt(r.getSyncedAt())
                        .build())
                .toList();
        return PageResponse.<CachedDagRunResponse>builder()
                .content(mapped)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<CachedDagDebugResponse> listCachedDebug(
            String deploymentIdFilter, Boolean errorsOnly, int page, int size) {
        List<String> scope = resolveDeploymentScope(deploymentIdFilter);
        if (scope.isEmpty()) {
            return emptyPage(page, size);
        }
        Sort sort = Sort.by(Sort.Direction.ASC, "dagId");
        PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 200), sort);
        Page<CachedDagMeta> result;
        if (scope.size() == 1) {
            String dep = scope.get(0);
            if (Boolean.TRUE.equals(errorsOnly)) {
                result = cachedDagMetaRepository.findAllByDeploymentIdInAndImportError(List.of(dep), true, pr);
            } else {
                result = cachedDagMetaRepository.findAllByDeploymentId(dep, pr);
            }
        } else {
            if (Boolean.TRUE.equals(errorsOnly)) {
                result = cachedDagMetaRepository.findAllByDeploymentIdInAndImportError(scope, true, pr);
            } else {
                result = cachedDagMetaRepository.findAllByDeploymentIdIn(scope, pr);
            }
        }
        Map<String, String> names = deploymentNames(result.getContent().stream()
                .map(CachedDagMeta::getDeploymentId)
                .collect(Collectors.toSet()));
        List<CachedDagDebugResponse> mapped = result.getContent().stream()
                .map(m -> CachedDagDebugResponse.builder()
                        .deploymentId(m.getDeploymentId())
                        .deploymentName(names.getOrDefault(m.getDeploymentId(), m.getDeploymentId()))
                        .dagId(m.getDagId())
                        .paused(m.isPaused())
                        .active(m.getActive())
                        .fileloc(m.getFileloc())
                        .owners(m.getOwners())
                        .description(m.getDescription())
                        .importError(m.isImportError())
                        .importErrorStackTrace(m.getImportErrorStackTrace())
                        .syncedAt(m.getSyncedAt())
                        .build())
                .toList();
        return PageResponse.<CachedDagDebugResponse>builder()
                .content(mapped)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<CachedDagImportErrorResponse> listCachedImportErrors(
            String deploymentIdFilter, int page, int size) {
        List<String> scope = resolveDeploymentScope(deploymentIdFilter);
        if (scope.isEmpty()) {
            return emptyPage(page, size);
        }
        PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Direction.ASC, "filename"));
        Page<CachedDagImportError> result = cachedDagImportErrorRepository.findAllByDeploymentIdIn(scope, pr);
        Map<String, String> names = deploymentNames(result.getContent().stream()
                .map(CachedDagImportError::getDeploymentId)
                .collect(Collectors.toSet()));
        List<CachedDagImportErrorResponse> mapped = result.getContent().stream()
                .map(e -> CachedDagImportErrorResponse.builder()
                        .deploymentId(e.getDeploymentId())
                        .deploymentName(names.getOrDefault(e.getDeploymentId(), e.getDeploymentId()))
                        .filename(e.getFilename())
                        .stackTrace(e.getStackTrace())
                        .sourceTimestamp(e.getSourceTimestamp())
                        .syncedAt(e.getSyncedAt())
                        .build())
                .toList();
        return PageResponse.<CachedDagImportErrorResponse>builder()
                .content(mapped)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public List<DagInsightSyncStatusResponse> listSyncStatuses(String deploymentIdFilter) {
        List<String> scope = resolveDeploymentScope(deploymentIdFilter);
        return dagInsightsSyncService.listSyncStatuses(scope);
    }

    private List<String> resolveDeploymentScope(String deploymentIdFilter) {
        if (StringUtils.hasText(deploymentIdFilter)) {
            String depId = deploymentIdFilter.trim();
            AirflowDeployment d = deploymentRepository.findByDeploymentId(depId)
                    .orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + depId));
            SecurityUtils.assertTenantInScope(d.getTenant().getTenantId());
            return List.of(d.getDeploymentId());
        }
        if (SecurityUtils.isAdmin()) {
            return deploymentRepository.findAll().stream()
                    .map(AirflowDeployment::getDeploymentId)
                    .toList();
        }
        String tenant = SecurityUtils.getNonAdminTenantScope()
                .orElseThrow(() -> new AccessDeniedException("Not authorized"));
        return deploymentRepository.findByTenantTenantId(tenant).stream()
                .map(AirflowDeployment::getDeploymentId)
                .toList();
    }

    private Map<String, String> deploymentNames(java.util.Collection<String> deploymentIds) {
        Map<String, String> map = new HashMap<>();
        for (String id : deploymentIds) {
            map.put(id, deploymentRepository.findByDeploymentId(id)
                    .map(AirflowDeployment::getName)
                    .orElse(id));
        }
        return map;
    }

    private static <T> PageResponse<T> emptyPage(int page, int size) {
        return PageResponse.<T>builder()
                .content(new ArrayList<>())
                .page(Math.max(0, page))
                .size(size)
                .totalElements(0)
                .totalPages(0)
                .build();
    }
}

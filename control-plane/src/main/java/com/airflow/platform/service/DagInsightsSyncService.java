package com.airflow.platform.service;

import com.airflow.platform.dto.DagInsightSyncStatusResponse;
import com.airflow.platform.model.AirflowDeployment;
import com.airflow.platform.model.CachedDagImportError;
import com.airflow.platform.model.CachedDagMeta;
import com.airflow.platform.model.CachedDagRun;
import com.airflow.platform.model.DagInsightSyncStatus;
import com.airflow.platform.repository.AirflowDeploymentRepository;
import com.airflow.platform.repository.CachedDagImportErrorRepository;
import com.airflow.platform.repository.CachedDagMetaRepository;
import com.airflow.platform.repository.CachedDagRunRepository;
import com.airflow.platform.repository.DagInsightSyncStatusRepository;
import com.airflow.platform.util.AirflowVersionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pulls DAG metadata, import errors, and recent DAG runs from each deployment's Airflow API into the control-plane DB.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DagInsightsSyncService {

    private final AirflowDeploymentRepository deploymentRepository;
    private final AirflowRemoteApiService airflowRemoteApiService;
    private final CachedDagRunRepository cachedDagRunRepository;
    private final CachedDagMetaRepository cachedDagMetaRepository;
    private final CachedDagImportErrorRepository cachedDagImportErrorRepository;
    private final DagInsightSyncStatusRepository dagInsightSyncStatusRepository;
    private final TransactionTemplate transactionTemplate;

    @Value("${dag-insights.sync.page-size:100}")
    private int dagsPageSize;

    @Value("${dag-insights.sync.max-dags-per-deployment:500}")
    private int maxDagsPerDeployment;

    @Value("${dag-insights.sync.max-runs-per-dag:25}")
    private int maxRunsPerDag;

    @Async("dagInsightsExecutor")
    public void triggerSyncDeployment(String deploymentId) {
        syncDeploymentInternal(deploymentId);
    }

    @Async("dagInsightsExecutor")
    public void triggerSyncAllRunnableDeployments() {
        syncAllRunnableDeployments();
    }

    @Async("dagInsightsExecutor")
    public void triggerSyncDeploymentsForTenant(String tenantId) {
        syncDeploymentsForTenant(tenantId);
    }

    /**
     * Scheduled / ops: all RUNNING (or UPDATING) deployments with a webserver URL.
     */
    public void syncAllRunnableDeployments() {
        List<AirflowDeployment> targets = deploymentRepository.findByStatusInAndWebserverUrlIsNotNull(
                List.of(AirflowDeployment.DeploymentStatus.RUNNING, AirflowDeployment.DeploymentStatus.UPDATING));
        for (AirflowDeployment d : targets) {
            try {
                syncDeploymentInternal(d.getDeploymentId());
            } catch (Exception e) {
                log.warn("DAG insights sync failed for {}: {}", d.getDeploymentId(), e.getMessage());
            }
        }
    }

    /**
     * Sync deployments in a tenant (non-admin manual refresh).
     */
    public void syncDeploymentsForTenant(String tenantId) {
        List<AirflowDeployment> all = deploymentRepository.findByTenantTenantId(tenantId);
        for (AirflowDeployment d : all) {
            if (d.getWebserverUrl() == null || d.getWebserverUrl().isBlank()) {
                continue;
            }
            if (d.getStatus() != AirflowDeployment.DeploymentStatus.RUNNING
                    && d.getStatus() != AirflowDeployment.DeploymentStatus.UPDATING) {
                continue;
            }
            try {
                syncDeploymentInternal(d.getDeploymentId());
            } catch (Exception e) {
                log.warn("DAG insights sync failed for {}: {}", d.getDeploymentId(), e.getMessage());
            }
        }
    }

    /**
     * Sync a single deployment by id (caller enforces tenant access).
     */
    public void syncDeploymentInternal(String deploymentId) {
        AirflowDeployment dep = deploymentRepository.findByDeploymentId(deploymentId)
                .orElseThrow(() -> new com.airflow.platform.exception.ResourceNotFoundException(
                        "Deployment not found: " + deploymentId));
        if (dep.getWebserverUrl() == null || dep.getWebserverUrl().isBlank()) {
            markSyncSkipped(dep.getDeploymentId(), "No webserver URL");
            return;
        }
        if (dep.getStatus() != AirflowDeployment.DeploymentStatus.RUNNING
                && dep.getStatus() != AirflowDeployment.DeploymentStatus.UPDATING) {
            markSyncSkipped(dep.getDeploymentId(), "Deployment not active (status=" + dep.getStatus() + ")");
            return;
        }

        Instant syncStart = Instant.now();
        transactionTemplate.executeWithoutResult(ts -> upsertSyncStarted(dep.getDeploymentId(), syncStart));

        try {
            Snapshot snap = collectSnapshot(dep);
            Instant syncedAt = Instant.now();
            transactionTemplate.executeWithoutResult(ts -> persistSnapshot(dep.getDeploymentId(), snap, syncedAt));
            transactionTemplate.executeWithoutResult(ts -> markSyncDone(dep.getDeploymentId(), syncStart, true, null));
        } catch (HttpClientErrorException e) {
            String msg = "Airflow HTTP " + e.getStatusCode().value() + ": " + e.getMessage();
            log.warn("DAG insights sync {}: {}", deploymentId, msg);
            transactionTemplate.executeWithoutResult(ts -> markSyncDone(dep.getDeploymentId(), syncStart, false, msg));
        } catch (RestClientException e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("DAG insights sync {}: {}", deploymentId, msg);
            transactionTemplate.executeWithoutResult(ts -> markSyncDone(dep.getDeploymentId(), syncStart, false, msg));
        }
    }

    private void markSyncSkipped(String deploymentId, String reason) {
        transactionTemplate.executeWithoutResult(ts -> markSyncDone(deploymentId, Instant.now(), false, reason));
    }

    private void upsertSyncStarted(String deploymentId, Instant started) {
        DagInsightSyncStatus row = dagInsightSyncStatusRepository.findById(deploymentId).orElseGet(DagInsightSyncStatus::new);
        row.setDeploymentId(deploymentId);
        row.setLastSyncStartedAt(started);
        row.setLastSyncSuccess(null);
        row.setLastErrorMessage(null);
        dagInsightSyncStatusRepository.save(row);
    }

    private void markSyncDone(String deploymentId, Instant started, boolean ok, String err) {
        DagInsightSyncStatus row = dagInsightSyncStatusRepository.findById(deploymentId).orElseGet(DagInsightSyncStatus::new);
        row.setDeploymentId(deploymentId);
        if (row.getLastSyncStartedAt() == null) {
            row.setLastSyncStartedAt(started);
        }
        row.setLastSyncCompletedAt(Instant.now());
        row.setLastSyncSuccess(ok);
        row.setLastErrorMessage(err != null && err.length() > 4000 ? err.substring(0, 4000) : err);
        dagInsightSyncStatusRepository.save(row);
    }

    private void persistSnapshot(String deploymentId, Snapshot snap, Instant syncedAt) {
        cachedDagImportErrorRepository.deleteByDeploymentId(deploymentId);
        cachedDagMetaRepository.deleteByDeploymentId(deploymentId);
        cachedDagRunRepository.deleteByDeploymentId(deploymentId);

        for (CachedDagImportError e : snap.importErrors()) {
            e.setDeploymentId(deploymentId);
            e.setSyncedAt(syncedAt);
        }
        cachedDagImportErrorRepository.saveAll(snap.importErrors());

        for (CachedDagMeta m : snap.meta()) {
            m.setDeploymentId(deploymentId);
            m.setSyncedAt(syncedAt);
        }
        cachedDagMetaRepository.saveAll(snap.meta());

        for (CachedDagRun r : snap.runs()) {
            r.setDeploymentId(deploymentId);
            r.setSyncedAt(syncedAt);
        }
        cachedDagRunRepository.saveAll(snap.runs());
    }

    private Snapshot collectSnapshot(AirflowDeployment dep) {
        String version = dep.getAirflowVersion();
        String baseWeb = dep.getWebserverUrl();
        boolean v3 = AirflowVersionUtils.isAirflow3OrLater(version);

        List<CachedDagImportError> importRows = fetchImportErrors(baseWeb, version, v3);
        Map<String, String> fileToStack = indexImportErrorsByNormalizedFile(importRows);

        List<Map<String, Object>> dagMaps = listAllDags(baseWeb, version, v3);
        Instant now = Instant.now();
        List<CachedDagMeta> metaList = new ArrayList<>();
        List<CachedDagRun> runs = new ArrayList<>();

        int dagCount = 0;
        for (Map<String, Object> dag : dagMaps) {
            if (dagCount >= maxDagsPerDeployment) {
                break;
            }
            String dagId = stringVal(dag.get("dag_id"));
            if (dagId == null || dagId.isBlank()) {
                continue;
            }
            dagCount++;

            boolean paused = Boolean.TRUE.equals(dag.get("is_paused"));
            Boolean active = null;
            if (dag.containsKey("is_active")) {
                Object a = dag.get("is_active");
                if (a instanceof Boolean b) {
                    active = b;
                }
            }
            String fileloc = stringVal(dag.get("fileloc"));
            String owners = stringVal(dag.get("owners"));
            String description = stringVal(dag.get("description"));

            String stack = matchImportStack(fileloc, fileToStack);
            boolean impErr = stack != null;

            CachedDagMeta m = new CachedDagMeta();
            m.setDagId(dagId);
            m.setPaused(paused);
            m.setActive(active);
            m.setFileloc(fileloc);
            m.setOwners(owners);
            m.setDescription(description);
            m.setImportError(impErr);
            m.setImportErrorStackTrace(stack);
            metaList.add(m);

            runs.addAll(fetchDagRuns(baseWeb, version, v3, dagId, now));
        }

        return new Snapshot(importRows, metaList, runs);
    }

    private List<CachedDagImportError> fetchImportErrors(String web, String version, boolean v3) {
        try {
            if (v3) {
                try {
                    return parseImportErrors(airflowRemoteApiService.getJson(web, version, "/api/v2/importErrors"));
                } catch (HttpClientErrorException e) {
                    if (e.getStatusCode() != HttpStatus.NOT_FOUND) {
                        throw e;
                    }
                }
            }
            return parseImportErrors(airflowRemoteApiService.getJson(web, version, "/api/v1/importErrors"));
        } catch (HttpClientErrorException e) {
            if (v3 && e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Collections.emptyList();
            }
            throw e;
        }
    }

    private List<CachedDagImportError> parseImportErrors(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return Collections.emptyList();
        }
        Object raw = body.get("import_errors");
        if (!(raw instanceof List<?> list)) {
            return Collections.emptyList();
        }
        List<CachedDagImportError> out = new ArrayList<>();
        Instant synced = Instant.now();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) map;
            String filename = stringVal(m.get("filename"));
            if (filename == null) {
                continue;
            }
            CachedDagImportError row = new CachedDagImportError();
            row.setFilename(truncate(filename, 2000));
            row.setStackTrace(stringVal(m.get("stack_trace")));
            row.setSourceTimestamp(parseInstant(m.get("timestamp")));
            row.setSyncedAt(synced);
            out.add(row);
        }
        return out;
    }

    private static Map<String, String> indexImportErrorsByNormalizedFile(List<CachedDagImportError> rows) {
        Map<String, String> map = new HashMap<>();
        for (CachedDagImportError e : rows) {
            if (e.getFilename() == null) {
                continue;
            }
            String key = normalizePath(e.getFilename());
            map.putIfAbsent(key, e.getStackTrace());
        }
        return map;
    }

    private static String matchImportStack(String fileloc, Map<String, String> normalizedFileToStack) {
        if (fileloc == null) {
            return null;
        }
        String fl = normalizePath(fileloc);
        for (Map.Entry<String, String> e : normalizedFileToStack.entrySet()) {
            if (fl.equals(e.getKey()) || fl.endsWith("/" + e.getKey()) || fl.endsWith(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    private static String normalizePath(String p) {
        return p.replace('\\', '/').trim();
    }

    private List<Map<String, Object>> listAllDags(String web, String version, boolean v3) {
        String prefix = v3 ? "/api/v2/dags" : "/api/v1/dags";
        List<Map<String, Object>> all = new ArrayList<>();
        int offset = 0;
        while (all.size() < maxDagsPerDeployment) {
            String q = prefix + "?limit=" + dagsPageSize + "&offset=" + offset + "&order_by=dag_id";
            Map<String, Object> page = airflowRemoteApiService.getJson(web, version, q);
            List<Map<String, Object>> chunk = extractObjectList(page, "dags");
            if (chunk.isEmpty()) {
                break;
            }
            for (Map<String, Object> d : chunk) {
                if (all.size() >= maxDagsPerDeployment) {
                    break;
                }
                all.add(d);
            }
            if (chunk.size() < dagsPageSize) {
                break;
            }
            offset += dagsPageSize;
        }
        return all;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractObjectList(Map<String, Object> page, String key) {
        Object raw = page.get(key);
        if (!(raw instanceof List<?> list)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    private List<CachedDagRun> fetchDagRuns(String web, String version, boolean v3, String dagId, Instant syncedAt) {
        String enc = UriUtils.encodePathSegment(dagId, StandardCharsets.UTF_8);
        String path = (v3 ? "/api/v2/dags/" : "/api/v1/dags/") + enc
                + "/dagRuns?limit=" + maxRunsPerDag + "&order_by=-start_date";
        Map<String, Object> body;
        try {
            body = airflowRemoteApiService.getJson(web, version, path);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Collections.emptyList();
            }
            log.debug("dagRuns list failed for {}: {}", dagId, e.getMessage());
            return Collections.emptyList();
        }
        List<Map<String, Object>> drs = extractObjectList(body, "dag_runs");
        List<CachedDagRun> out = new ArrayList<>();
        for (Map<String, Object> dr : drs) {
            String runId = stringVal(dr.get("dag_run_id"));
            if (runId == null) {
                continue;
            }
            CachedDagRun row = new CachedDagRun();
            row.setDagId(dagId);
            row.setDagRunId(truncate(runId, 250));
            row.setState(stringVal(dr.get("state")));
            row.setLogicalDate(firstNonNullInstant(dr.get("logical_date"), dr.get("execution_date")));
            row.setStartDate(parseInstant(dr.get("start_date")));
            row.setEndDate(parseInstant(dr.get("end_date")));
            row.setRunType(stringVal(dr.get("run_type")));
            row.setSyncedAt(syncedAt);
            out.add(row);
        }
        return out;
    }

    private static Instant firstNonNullInstant(Object a, Object b) {
        Instant x = parseInstant(a);
        if (x != null) {
            return x;
        }
        return parseInstant(b);
    }

    private static String stringVal(Object o) {
        return o == null ? null : Objects.toString(o, null);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static Instant parseInstant(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Instant i) {
            return i;
        }
        String s = v.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(s).toInstant();
            } catch (DateTimeParseException ignored2) {
                return null;
            }
        }
    }

    private record Snapshot(
            List<CachedDagImportError> importErrors,
            List<CachedDagMeta> meta,
            List<CachedDagRun> runs) {
    }

    /**
     * Deployments the caller may see, with sync status for UI.
     */
    public List<DagInsightSyncStatusResponse> listSyncStatuses(Collection<String> deploymentIds) {
        if (deploymentIds.isEmpty()) {
            return List.of();
        }
        List<DagInsightSyncStatusResponse> out = new ArrayList<>();
        for (String depId : deploymentIds) {
            DagInsightSyncStatus st = dagInsightSyncStatusRepository.findById(depId).orElse(null);
            String name = deploymentRepository.findByDeploymentId(depId).map(AirflowDeployment::getName).orElse(depId);
            out.add(DagInsightSyncStatusResponse.builder()
                    .deploymentId(depId)
                    .deploymentName(name)
                    .lastSyncStartedAt(st != null ? st.getLastSyncStartedAt() : null)
                    .lastSyncCompletedAt(st != null ? st.getLastSyncCompletedAt() : null)
                    .lastSyncSuccess(st != null ? st.getLastSyncSuccess() : null)
                    .lastErrorMessage(st != null ? st.getLastErrorMessage() : null)
                    .build());
        }
        return out;
    }
}

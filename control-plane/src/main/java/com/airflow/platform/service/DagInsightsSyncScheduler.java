package com.airflow.platform.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically refreshes cached DAG runs and debug metadata from each runnable Airflow deployment.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "dag-insights.sync.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class DagInsightsSyncScheduler {

    private final DagInsightsSyncService dagInsightsSyncService;

    @Scheduled(fixedDelayString = "${dag-insights.sync-interval-ms:300000}")
    public void runSync() {
        log.debug("DAG insights scheduled sync");
        try {
            dagInsightsSyncService.syncAllRunnableDeployments();
        } catch (Exception e) {
            log.warn("DAG insights scheduled sync failed: {}", e.getMessage());
        }
    }
}

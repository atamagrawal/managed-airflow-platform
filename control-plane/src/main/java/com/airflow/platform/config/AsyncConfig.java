package com.airflow.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.Executor;

/**
 * Enables {@code @Async} for background tasks (e.g. local Docker FAB user sync after deployment).
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String LOCAL_FAB_SYNC_EXECUTOR = "localFabSyncExecutor";
    public static final String DAG_INSIGHTS_EXECUTOR = "dagInsightsExecutor";

    @Bean(name = LOCAL_FAB_SYNC_EXECUTOR)
    public Executor localFabSyncExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(1);
        ex.setMaxPoolSize(2);
        ex.setQueueCapacity(100);
        ex.setThreadNamePrefix("local-fab-sync-");
        ex.initialize();
        return ex;
    }

    @Bean(name = DAG_INSIGHTS_EXECUTOR)
    public Executor dagInsightsExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(6);
        ex.setQueueCapacity(100);
        ex.setThreadNamePrefix("dag-insights-");
        ex.initialize();
        return ex;
    }

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}

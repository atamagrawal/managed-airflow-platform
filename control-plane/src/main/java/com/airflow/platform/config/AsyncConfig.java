package com.airflow.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Enables {@code @Async} for background tasks (e.g. local Docker FAB user sync after deployment).
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String LOCAL_FAB_SYNC_EXECUTOR = "localFabSyncExecutor";

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
}

package com.airflow.platform.config;

import com.airflow.platform.model.AirflowDeployment;
import com.airflow.platform.provider.impl.LocalDeploymentProvider;
import com.airflow.platform.repository.AirflowDeploymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Refreshes {@code config/airflow_local_settings.py} for every local deployment on each control-plane start. Stacks
 * that predate the handoff patch, or that were never project-synced after a template change, otherwise keep running
 * without the FAB /auth CORS + {@code _token} cookie middleware.
 */
@Component
@ConditionalOnProperty(name = "deployment.provider", havingValue = "local")
@RequiredArgsConstructor
@Slf4j
public class LocalAirflowHandoffConfigEnsurer {

    private static final int ORDER_AFTER_DEFAULT_DEPLOYMENT_BOOTSTRAP = 100;

    private final AirflowDeploymentRepository deploymentRepository;
    private final LocalDeploymentProvider localDeploymentProvider;

    @EventListener(ApplicationReadyEvent.class)
    @Order(ORDER_AFTER_DEFAULT_DEPLOYMENT_BOOTSTRAP)
    public void ensureHandoffPatchFiles() {
        List<AirflowDeployment> all = deploymentRepository.findAll();
        if (all.isEmpty()) {
            return;
        }
        for (AirflowDeployment d : all) {
            localDeploymentProvider.ensureAirflowHandoffConfig(d);
        }
        log.info(
                "Checked airflow_local_settings.py for {} local deployment row(s); "
                        + "when the file content changed, running airflow-apiserver was restarted to load the handoff patch.",
                all.size());
    }
}

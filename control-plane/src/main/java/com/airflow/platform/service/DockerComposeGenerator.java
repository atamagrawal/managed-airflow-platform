package com.airflow.platform.service;

import com.airflow.platform.model.AirflowDeployment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Generates Docker Compose files for Airflow deployments
 */
@Service
@ConditionalOnProperty(name = "deployment.provider", havingValue = "ec2")
@Slf4j
public class DockerComposeGenerator {

    /**
     * Generate a docker-compose.yml file for an Airflow deployment
     */
    public String generateDockerCompose(AirflowDeployment deployment) {
        log.info("Generating docker-compose.yml for deployment: {}", deployment.getDeploymentId());

        String executorType = getExecutorType(deployment.getExecutorType());
        boolean needsRedis = deployment.getExecutorType() == AirflowDeployment.ExecutorType.CELERY ||
                             deployment.getExecutorType() == AirflowDeployment.ExecutorType.CELERY_KUBERNETES;

        StringBuilder compose = new StringBuilder();

        // Header
        compose.append("version: '3.8'\n\n");

        // Common environment variables
        compose.append("x-airflow-common:\n");
        compose.append("  &airflow-common\n");
        compose.append("  image: apache/airflow:").append(deployment.getAirflowVersion()).append("\n");
        compose.append("  environment:\n");
        compose.append("    AIRFLOW__CORE__EXECUTOR: ").append(executorType).append("\n");
        compose.append("    AIRFLOW__DATABASE__SQL_ALCHEMY_CONN: postgresql+psycopg2://airflow:airflow@postgres:5432/airflow\n");

        if (needsRedis) {
            compose.append("    AIRFLOW__CELERY__BROKER_URL: redis://redis:6379/0\n");
            compose.append("    AIRFLOW__CELERY__RESULT_BACKEND: db+postgresql://airflow:airflow@postgres:5432/airflow\n");
        }

        compose.append("    AIRFLOW__CORE__FERNET_KEY: ''\n");
        compose.append("    AIRFLOW__CORE__DAGS_ARE_PAUSED_AT_CREATION: 'true'\n");
        compose.append("    AIRFLOW__CORE__LOAD_EXAMPLES: 'false'\n");
        compose.append("    AIRFLOW__API__AUTH_BACKENDS: 'airflow.api.auth.backend.basic_auth'\n");
        compose.append("    AIRFLOW__WEBSERVER__SECRET_KEY: 'airflow-secret-key'\n");
        compose.append("    _PIP_ADDITIONAL_REQUIREMENTS: ''\n");

        compose.append("  volumes:\n");
        compose.append("    - ./dags:/opt/airflow/dags\n");
        compose.append("    - ./logs:/opt/airflow/logs\n");
        compose.append("    - ./plugins:/opt/airflow/plugins\n");
        compose.append("  user: \"50000:0\"\n");
        compose.append("  depends_on:\n");
        compose.append("    postgres:\n");
        compose.append("      condition: service_healthy\n");

        if (needsRedis) {
            compose.append("    redis:\n");
            compose.append("      condition: service_healthy\n");
        }

        compose.append("  networks:\n");
        compose.append("    - airflow-network\n\n");

        // Services
        compose.append("services:\n\n");

        // PostgreSQL
        compose.append("  postgres:\n");
        compose.append("    image: postgres:13\n");
        compose.append("    environment:\n");
        compose.append("      POSTGRES_USER: airflow\n");
        compose.append("      POSTGRES_PASSWORD: airflow\n");
        compose.append("      POSTGRES_DB: airflow\n");
        compose.append("    volumes:\n");
        compose.append("      - postgres-db-volume:/var/lib/postgresql/data\n");
        compose.append("    healthcheck:\n");
        compose.append("      test: [\"CMD\", \"pg_isready\", \"-U\", \"airflow\"]\n");
        compose.append("      interval: 5s\n");
        compose.append("      retries: 5\n");
        compose.append("    restart: always\n");
        compose.append("    networks:\n");
        compose.append("      - airflow-network\n\n");

        // Redis (if needed)
        if (needsRedis) {
            compose.append("  redis:\n");
            compose.append("    image: redis:latest\n");
            compose.append("    expose:\n");
            compose.append("      - 6379\n");
            compose.append("    healthcheck:\n");
            compose.append("      test: [\"CMD\", \"redis-cli\", \"ping\"]\n");
            compose.append("      interval: 5s\n");
            compose.append("      timeout: 30s\n");
            compose.append("      retries: 50\n");
            compose.append("    restart: always\n");
            compose.append("    networks:\n");
            compose.append("      - airflow-network\n\n");
        }

        // Webserver
        compose.append("  airflow-webserver:\n");
        compose.append("    <<: *airflow-common\n");
        compose.append("    command: webserver\n");
        compose.append("    ports:\n");
        compose.append("      - 8080:8080\n");
        compose.append("    healthcheck:\n");
        compose.append("      test: [\"CMD\", \"curl\", \"--fail\", \"http://localhost:8080/health\"]\n");
        compose.append("      interval: 10s\n");
        compose.append("      timeout: 10s\n");
        compose.append("      retries: 5\n");
        compose.append("    restart: always\n");
        compose.append("    deploy:\n");
        compose.append("      resources:\n");
        compose.append("        limits:\n");
        compose.append("          cpus: '").append(getCpuLimit(deployment.getWebserverCpu())).append("'\n");
        compose.append("          memory: ").append(deployment.getWebserverMemory()).append("M\n\n");

        // Scheduler
        compose.append("  airflow-scheduler:\n");
        compose.append("    <<: *airflow-common\n");
        compose.append("    command: scheduler\n");
        compose.append("    healthcheck:\n");
        compose.append("      test: [\"CMD-SHELL\", 'airflow jobs check --job-type SchedulerJob --hostname \"$${HOSTNAME}\"']\n");
        compose.append("      interval: 10s\n");
        compose.append("      timeout: 10s\n");
        compose.append("      retries: 5\n");
        compose.append("    restart: always\n");
        compose.append("    deploy:\n");
        compose.append("      resources:\n");
        compose.append("        limits:\n");
        compose.append("          cpus: '").append(getCpuLimit(deployment.getSchedulerCpu())).append("'\n");
        compose.append("          memory: ").append(deployment.getSchedulerMemory()).append("M\n\n");

        // Workers (if Celery)
        if (needsRedis) {
            compose.append("  airflow-worker:\n");
            compose.append("    <<: *airflow-common\n");
            compose.append("    command: celery worker\n");
            compose.append("    healthcheck:\n");
            compose.append("      test:\n");
            compose.append("        - \"CMD-SHELL\"\n");
            compose.append("        - 'celery --app airflow.executors.celery_executor.app inspect ping -d \"celery@$${HOSTNAME}\"'\n");
            compose.append("      interval: 10s\n");
            compose.append("      timeout: 10s\n");
            compose.append("      retries: 5\n");
            compose.append("    restart: always\n");
            compose.append("    deploy:\n");
            compose.append("      replicas: ").append(deployment.getMinWorkers()).append("\n");
            compose.append("      resources:\n");
            compose.append("        limits:\n");
            compose.append("          cpus: '").append(getCpuLimit(deployment.getWorkerCpu())).append("'\n");
            compose.append("          memory: ").append(deployment.getWorkerMemory()).append("M\n\n");

            // Flower (Celery monitoring)
            compose.append("  airflow-flower:\n");
            compose.append("    <<: *airflow-common\n");
            compose.append("    command: celery flower\n");
            compose.append("    ports:\n");
            compose.append("      - 5555:5555\n");
            compose.append("    healthcheck:\n");
            compose.append("      test: [\"CMD\", \"curl\", \"--fail\", \"http://localhost:5555/\"]\n");
            compose.append("      interval: 10s\n");
            compose.append("      timeout: 10s\n");
            compose.append("      retries: 5\n");
            compose.append("    restart: always\n\n");
        }

        // Init service
        compose.append("  airflow-init:\n");
        compose.append("    <<: *airflow-common\n");
        compose.append("    entrypoint: /bin/bash\n");
        compose.append("    command:\n");
        compose.append("      - -c\n");
        compose.append("      - |\n");
        compose.append("        mkdir -p /opt/airflow/dags /opt/airflow/logs /opt/airflow/plugins\n");
        compose.append("        chown -R 50000:0 /opt/airflow/{dags,logs,plugins}\n");
        compose.append("        exec /entrypoint airflow db init && \\\n");
        compose.append("        airflow users create \\\n");
        compose.append("          --username admin \\\n");
        compose.append("          --firstname Admin \\\n");
        compose.append("          --lastname User \\\n");
        compose.append("          --role Admin \\\n");
        compose.append("          --email admin@example.com \\\n");
        compose.append("          --password admin\n");
        compose.append("    user: \"0:0\"\n");
        compose.append("    restart: on-failure\n\n");

        // Volumes
        compose.append("volumes:\n");
        compose.append("  postgres-db-volume:\n\n");

        // Networks
        compose.append("networks:\n");
        compose.append("  airflow-network:\n");
        compose.append("    name: ").append(deployment.getDeploymentId()).append("-network\n");

        return compose.toString();
    }

    /**
     * Generate an .env file for the deployment
     */
    public String generateEnvFile(AirflowDeployment deployment) {
        StringBuilder env = new StringBuilder();

        env.append("AIRFLOW_UID=50000\n");
        env.append("AIRFLOW_GID=0\n");
        env.append("DEPLOYMENT_ID=").append(deployment.getDeploymentId()).append("\n");
        env.append("AIRFLOW_VERSION=").append(deployment.getAirflowVersion()).append("\n");

        return env.toString();
    }

    private String getExecutorType(AirflowDeployment.ExecutorType executorType) {
        switch (executorType) {
            case LOCAL:
                return "LocalExecutor";
            case CELERY:
                return "CeleryExecutor";
            case KUBERNETES:
                return "KubernetesExecutor";
            case CELERY_KUBERNETES:
                return "CeleryKubernetesExecutor";
            default:
                return "LocalExecutor";
        }
    }

    private String getCpuLimit(String cpuString) {
        // Convert millicores (e.g., "500") to Docker CPU format (e.g., "0.5")
        try {
            int millicores = Integer.parseInt(cpuString);
            return String.format("%.2f", millicores / 1000.0);
        } catch (NumberFormatException e) {
            return "1.0";
        }
    }
}

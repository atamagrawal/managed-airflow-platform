package com.airflow.platform.service;

import com.airflow.platform.model.AirflowDeployment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

/**
 * Generates Docker Compose files for Airflow 3.x (api-server, dag-processor, init ordering).
 * Supports both EC2 and local deployments.
 */
@Service
@ConditionalOnExpression("'${deployment.provider}' == 'ec2' or '${deployment.provider}' == 'local'")
@Slf4j
public class DockerComposeGenerator {

    @Value("${deployment.provider}")
    private String deploymentProvider;

    public String generateDockerCompose(AirflowDeployment deployment) {
        log.info("Generating docker-compose.yml for deployment: {}", deployment.getDeploymentId());

        String executorType = getExecutorType(deployment.getExecutorType());
        boolean needsRedis = deployment.getExecutorType() == AirflowDeployment.ExecutorType.CELERY ||
                             deployment.getExecutorType() == AirflowDeployment.ExecutorType.CELERY_KUBERNETES;

        StringBuilder compose = new StringBuilder();

        compose.append("version: '3.9'\n\n");

        compose.append("x-airflow-common-env:\n");
        compose.append("  &airflow-common-env\n");
        compose.append("  AIRFLOW__CORE__EXECUTOR: ").append(executorType).append("\n");
        compose.append("  AIRFLOW__DATABASE__SQL_ALCHEMY_CONN: postgresql+psycopg2://airflow:airflow@postgres:5432/airflow\n");
        compose.append("  AIRFLOW__CORE__AUTH_MANAGER: airflow.providers.fab.auth_manager.fab_auth_manager.FabAuthManager\n");
        compose.append("  AIRFLOW__CORE__EXECUTION_API_SERVER_URL: http://airflow-apiserver:8080/execution/\n");
        compose.append("  AIRFLOW__API_AUTH__JWT_SECRET: airflow_jwt_secret\n");
        compose.append("  AIRFLOW__API_AUTH__JWT_ISSUER: airflow\n");
        compose.append("  AIRFLOW__CORE__FERNET_KEY: ''\n");
        compose.append("  AIRFLOW__CORE__DAGS_ARE_PAUSED_AT_CREATION: 'true'\n");
        compose.append("  AIRFLOW__CORE__LOAD_EXAMPLES: 'false'\n");
        compose.append("  AIRFLOW__SCHEDULER__ENABLE_HEALTH_CHECK: 'true'\n");
        compose.append("  _PIP_ADDITIONAL_REQUIREMENTS: ''\n");

        if (needsRedis) {
            compose.append("  AIRFLOW__CELERY__BROKER_URL: redis://:@redis:6379/0\n");
            compose.append("  AIRFLOW__CELERY__RESULT_BACKEND: db+postgresql+psycopg2://airflow:airflow@postgres:5432/airflow\n");
        }

        compose.append("\n");
        compose.append("x-airflow-common:\n");
        compose.append("  &airflow-common\n");
        compose.append("  image: apache/airflow:").append(deployment.getAirflowVersion()).append("\n");
        compose.append("  environment:\n");
        compose.append("    <<: *airflow-common-env\n");
        compose.append("  volumes:\n");
        compose.append("    - ./dags:/opt/airflow/dags\n");
        compose.append("    - ./logs:/opt/airflow/logs\n");
        compose.append("    - ./config:/opt/airflow/config\n");
        compose.append("    - ./plugins:/opt/airflow/plugins\n");
        compose.append("  user: \"50000:0\"\n");
        compose.append("  networks:\n");
        compose.append("    - airflow-network\n\n");

        compose.append("services:\n\n");

        compose.append("  postgres:\n");
        compose.append("    image: postgres:16\n");
        compose.append("    environment:\n");
        compose.append("      POSTGRES_USER: airflow\n");
        compose.append("      POSTGRES_PASSWORD: airflow\n");
        compose.append("      POSTGRES_DB: airflow\n");
        compose.append("    volumes:\n");
        compose.append("      - postgres-db-volume:/var/lib/postgresql/data\n");
        compose.append("    healthcheck:\n");
        compose.append("      test: [\"CMD-SHELL\", \"pg_isready -h localhost -U airflow -d airflow || exit 1\"]\n");
        compose.append("      interval: 5s\n");
        compose.append("      timeout: 5s\n");
        compose.append("      retries: 20\n");
        compose.append("      start_period: 40s\n");
        compose.append("    restart: always\n");
        compose.append("    networks:\n");
        compose.append("      - airflow-network\n\n");

        if (needsRedis) {
            compose.append("  redis:\n");
            compose.append("    image: redis:7.2-bookworm\n");
            compose.append("    expose:\n");
            compose.append("      - 6379\n");
            compose.append("    healthcheck:\n");
            compose.append("      test: [\"CMD\", \"redis-cli\", \"ping\"]\n");
            compose.append("      interval: 10s\n");
            compose.append("      timeout: 30s\n");
            compose.append("      retries: 50\n");
            compose.append("      start_period: 30s\n");
            compose.append("    restart: always\n");
            compose.append("    networks:\n");
            compose.append("      - airflow-network\n\n");
        }

        compose.append("  airflow-init:\n");
        compose.append("    <<: *airflow-common\n");
        compose.append("    entrypoint: /bin/bash\n");
        compose.append("    command:\n");
        compose.append("      - -c\n");
        compose.append("      - |\n");
        compose.append("        mkdir -p /opt/airflow/dags /opt/airflow/logs /opt/airflow/plugins /opt/airflow/config\n");
        compose.append("        chown -R 50000:0 /opt/airflow/dags /opt/airflow/logs /opt/airflow/plugins /opt/airflow/config\n");
        compose.append("        /entrypoint airflow db migrate\n");
        compose.append("        /entrypoint airflow users create --username admin --firstname Admin --lastname User --role Admin --email admin@example.com "
                    + "--password admin || true\n");
        compose.append("    user: \"0:0\"\n");
        compose.append("    restart: \"no\"\n");
        compose.append("    depends_on:\n");
        compose.append("      postgres:\n");
        compose.append("        condition: service_healthy\n");
        if (needsRedis) {
            compose.append("      redis:\n");
            compose.append("        condition: service_healthy\n");
        }
        compose.append("\n");

        int apiserverPort = getApiserverPort(deployment);
        compose.append("  airflow-apiserver:\n");
        compose.append("    <<: *airflow-common\n");
        compose.append("    command: api-server\n");
        compose.append("    ports:\n");
        compose.append("      - ").append(apiserverPort).append(":8080\n");
        compose.append("    healthcheck:\n");
        compose.append("      test: [\"CMD\", \"curl\", \"--fail\", \"http://localhost:8080/api/v2/monitor/health\"]\n");
        compose.append("      interval: 30s\n");
        compose.append("      timeout: 10s\n");
        compose.append("      retries: 5\n");
        compose.append("      start_period: 30s\n");
        compose.append("    restart: always\n");
        compose.append("    depends_on:\n");
        compose.append("      postgres:\n");
        compose.append("        condition: service_healthy\n");
        if (needsRedis) {
            compose.append("      redis:\n");
            compose.append("        condition: service_healthy\n");
        }
        compose.append("      airflow-init:\n");
        compose.append("        condition: service_completed_successfully\n\n");

        compose.append("  airflow-scheduler:\n");
        compose.append("    <<: *airflow-common\n");
        compose.append("    command: scheduler\n");
        compose.append("    healthcheck:\n");
        compose.append("      test: [\"CMD\", \"curl\", \"--fail\", \"http://localhost:8974/health\"]\n");
        compose.append("      interval: 30s\n");
        compose.append("      timeout: 10s\n");
        compose.append("      retries: 5\n");
        compose.append("      start_period: 30s\n");
        compose.append("    restart: always\n");
        compose.append("    depends_on:\n");
        compose.append("      postgres:\n");
        compose.append("        condition: service_healthy\n");
        if (needsRedis) {
            compose.append("      redis:\n");
            compose.append("        condition: service_healthy\n");
        }
        compose.append("      airflow-init:\n");
        compose.append("        condition: service_completed_successfully\n\n");

        compose.append("  airflow-dag-processor:\n");
        compose.append("    <<: *airflow-common\n");
        compose.append("    command: dag-processor\n");
        compose.append("    healthcheck:\n");
        compose.append("      test: [\"CMD-SHELL\", 'airflow jobs check --job-type DagProcessorJob --hostname \"$${HOSTNAME}\"']\n");
        compose.append("      interval: 30s\n");
        compose.append("      timeout: 10s\n");
        compose.append("      retries: 5\n");
        compose.append("      start_period: 30s\n");
        compose.append("    restart: always\n");
        compose.append("    depends_on:\n");
        compose.append("      postgres:\n");
        compose.append("        condition: service_healthy\n");
        if (needsRedis) {
            compose.append("      redis:\n");
            compose.append("        condition: service_healthy\n");
        }
        compose.append("      airflow-init:\n");
        compose.append("        condition: service_completed_successfully\n\n");

        if (needsRedis) {
            compose.append("  airflow-worker:\n");
            compose.append("    <<: *airflow-common\n");
            compose.append("    command: celery worker\n");
            compose.append("    healthcheck:\n");
            compose.append("      test:\n");
            compose.append("        - \"CMD-SHELL\"\n");
            compose.append("        - 'celery --app airflow.providers.celery.executors.celery_executor.app inspect ping -d \"celery@$${HOSTNAME}\" "
                    + "|| celery --app airflow.executors.celery_executor.app inspect ping -d \"celery@$${HOSTNAME}\"'\n");
            compose.append("      interval: 30s\n");
            compose.append("      timeout: 10s\n");
            compose.append("      retries: 5\n");
            compose.append("      start_period: 30s\n");
            compose.append("    environment:\n");
            compose.append("      <<: *airflow-common-env\n");
            compose.append("      DUMB_INIT_SETSID: \"0\"\n");
            compose.append("    restart: always\n");
            compose.append("    depends_on:\n");
            compose.append("      postgres:\n");
            compose.append("        condition: service_healthy\n");
            compose.append("      redis:\n");
            compose.append("        condition: service_healthy\n");
            compose.append("      airflow-init:\n");
            compose.append("        condition: service_completed_successfully\n");
            compose.append("      airflow-apiserver:\n");
            compose.append("        condition: service_healthy\n\n");

            int flowerPort = getFlowerPort(deployment);
            compose.append("  airflow-flower:\n");
            compose.append("    <<: *airflow-common\n");
            compose.append("    command: celery flower\n");
            compose.append("    ports:\n");
            compose.append("      - ").append(flowerPort).append(":5555\n");
            compose.append("    healthcheck:\n");
            compose.append("      test: [\"CMD\", \"curl\", \"--fail\", \"http://localhost:5555/\"]\n");
            compose.append("      interval: 30s\n");
            compose.append("      timeout: 10s\n");
            compose.append("      retries: 5\n");
            compose.append("      start_period: 30s\n");
            compose.append("    restart: always\n");
            compose.append("    depends_on:\n");
            compose.append("      postgres:\n");
            compose.append("        condition: service_healthy\n");
            compose.append("      redis:\n");
            compose.append("        condition: service_healthy\n");
            compose.append("      airflow-init:\n");
            compose.append("        condition: service_completed_successfully\n\n");
        }

        compose.append("volumes:\n");
        compose.append("  postgres-db-volume:\n\n");

        compose.append("networks:\n");
        compose.append("  airflow-network:\n");
        compose.append("    name: ").append(deployment.getDeploymentId()).append("-network\n");

        return compose.toString();
    }

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

    /**
     * Published host port for airflow-apiserver (container listens on 8080).
     * Local profile uses 8090–8189 to avoid colliding with the control plane on 8080.
     */
    private int getApiserverPort(AirflowDeployment deployment) {
        if ("local".equals(deploymentProvider)) {
            int hash = Math.abs(deployment.getDeploymentId().hashCode());
            return 8090 + (hash % 100);
        }
        return 8080;
    }

    private int getFlowerPort(AirflowDeployment deployment) {
        if ("local".equals(deploymentProvider)) {
            int hash = Math.abs(deployment.getDeploymentId().hashCode());
            return 5555 + (hash % 100);
        }
        return 5555;
    }
}

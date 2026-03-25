package com.airflow.platform.provider.impl;

import com.airflow.platform.exception.DeploymentException;
import com.airflow.platform.model.AirflowDeployment;
import com.airflow.platform.provider.DeploymentProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.*;

import java.util.*;

/**
 * AWS ECS implementation of DeploymentProvider
 * Manages ECS task definitions, services, and Application Load Balancers
 */
@Component
@ConditionalOnProperty(name = "deployment.provider", havingValue = "ecs")
@RequiredArgsConstructor
@Slf4j
public class ECSDeploymentProvider implements DeploymentProvider {

    private final EcsClient ecsClient;

    @Value("${aws.ecs.cluster-prefix:managed-airflow}")
    private String clusterPrefix;

    @Value("${aws.ecs.task-execution-role-arn}")
    private String taskExecutionRoleArn;

    @Value("${aws.ecs.task-role-arn}")
    private String taskRoleArn;

    @Value("${aws.vpc.subnet-ids}")
    private List<String> subnetIds;

    @Value("${aws.vpc.security-group-ids}")
    private List<String> securityGroupIds;

    @Value("${aws.efs.file-system-id}")
    private String efsFileSystemId;

    @Value("${aws.efs.access-point-id:}")
    private String efsAccessPointId;

    @Override
    public void deploy(AirflowDeployment deployment) {
        log.info("Deploying Airflow to ECS with containerized PostgreSQL and Redis: {}", deployment.getDeploymentId());

        try {
            String clusterName = getClusterName(deployment);

            // Register task definitions for database and message broker
            String postgresTaskDef = registerPostgresTaskDefinition(deployment);
            String redisTaskDef = null;

            if (deployment.getExecutorType() == AirflowDeployment.ExecutorType.CELERY ||
                deployment.getExecutorType() == AirflowDeployment.ExecutorType.CELERY_KUBERNETES) {
                redisTaskDef = registerRedisTaskDefinition(deployment);
            }

            // Create database and message broker services first
            createPostgresService(deployment, clusterName, postgresTaskDef);
            if (redisTaskDef != null) {
                createRedisService(deployment, clusterName, redisTaskDef);
            }

            // Wait for database to be ready
            waitForServiceStable(clusterName, getServiceName(deployment, "postgres"));

            // Register task definitions for Airflow components
            String schedulerTaskDef = registerSchedulerTaskDefinition(deployment);
            String webserverTaskDef = registerWebserverTaskDefinition(deployment);
            String workerTaskDef = null;

            if (deployment.getExecutorType() == AirflowDeployment.ExecutorType.CELERY ||
                deployment.getExecutorType() == AirflowDeployment.ExecutorType.CELERY_KUBERNETES) {
                workerTaskDef = registerWorkerTaskDefinition(deployment);
            }

            // Create Airflow services
            createSchedulerService(deployment, clusterName, schedulerTaskDef);
            createWebserverService(deployment, clusterName, webserverTaskDef);

            if (workerTaskDef != null) {
                createWorkerService(deployment, clusterName, workerTaskDef);
            }

            log.info("Airflow deployed successfully to ECS: {}", deployment.getDeploymentId());
        } catch (Exception e) {
            log.error("Failed to deploy Airflow to ECS: {}", deployment.getDeploymentId(), e);
            throw new DeploymentException("ECS deployment failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void upgrade(AirflowDeployment deployment) {
        log.info("Upgrading Airflow deployment on ECS: {}", deployment.getDeploymentId());

        try {
            String clusterName = getClusterName(deployment);

            // Update task definitions
            String schedulerTaskDef = registerSchedulerTaskDefinition(deployment);
            String webserverTaskDef = registerWebserverTaskDefinition(deployment);

            // Update services with new task definitions
            updateService(clusterName, getServiceName(deployment, "scheduler"), schedulerTaskDef);
            updateService(clusterName, getServiceName(deployment, "webserver"), webserverTaskDef);

            if (deployment.getExecutorType() == AirflowDeployment.ExecutorType.CELERY ||
                deployment.getExecutorType() == AirflowDeployment.ExecutorType.CELERY_KUBERNETES) {
                String workerTaskDef = registerWorkerTaskDefinition(deployment);
                updateService(clusterName, getServiceName(deployment, "worker"), workerTaskDef);
            }

            log.info("Airflow upgraded successfully on ECS: {}", deployment.getDeploymentId());
        } catch (Exception e) {
            log.error("Failed to upgrade Airflow on ECS: {}", deployment.getDeploymentId(), e);
            throw new DeploymentException("ECS upgrade failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void uninstall(AirflowDeployment deployment) {
        log.info("Uninstalling Airflow from ECS: {}", deployment.getDeploymentId());

        try {
            String clusterName = getClusterName(deployment);

            // Delete Airflow services
            deleteService(clusterName, getServiceName(deployment, "scheduler"));
            deleteService(clusterName, getServiceName(deployment, "webserver"));

            if (deployment.getExecutorType() == AirflowDeployment.ExecutorType.CELERY ||
                deployment.getExecutorType() == AirflowDeployment.ExecutorType.CELERY_KUBERNETES) {
                deleteService(clusterName, getServiceName(deployment, "worker"));
                deleteService(clusterName, getServiceName(deployment, "redis"));
            }

            // Delete database service
            deleteService(clusterName, getServiceName(deployment, "postgres"));

            // Deregister task definitions
            deregisterTaskDefinition(getTaskDefinitionFamily(deployment, "scheduler"));
            deregisterTaskDefinition(getTaskDefinitionFamily(deployment, "webserver"));
            deregisterTaskDefinition(getTaskDefinitionFamily(deployment, "postgres"));

            if (deployment.getExecutorType() == AirflowDeployment.ExecutorType.CELERY ||
                deployment.getExecutorType() == AirflowDeployment.ExecutorType.CELERY_KUBERNETES) {
                deregisterTaskDefinition(getTaskDefinitionFamily(deployment, "worker"));
                deregisterTaskDefinition(getTaskDefinitionFamily(deployment, "redis"));
            }

            log.info("Airflow uninstalled successfully from ECS: {}", deployment.getDeploymentId());
        } catch (Exception e) {
            log.error("Failed to uninstall Airflow from ECS: {}", deployment.getDeploymentId(), e);
            throw new DeploymentException("ECS uninstall failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getDeploymentStatus(AirflowDeployment deployment) {
        try {
            String clusterName = getClusterName(deployment);
            String serviceName = getServiceName(deployment, "webserver");

            DescribeServicesRequest request = DescribeServicesRequest.builder()
                    .cluster(clusterName)
                    .services(serviceName)
                    .build();

            DescribeServicesResponse response = ecsClient.describeServices(request);

            if (!response.services().isEmpty()) {
                Service service = response.services().get(0);
                return service.status();
            }

            return "UNKNOWN";
        } catch (Exception e) {
            log.error("Failed to get deployment status: {}", deployment.getDeploymentId(), e);
            return "ERROR";
        }
    }

    @Override
    public String getWebserverUrl(AirflowDeployment deployment) {
        // In a real implementation, this would return the ALB DNS name
        if (deployment.getIngressHost() != null && !deployment.getIngressHost().isEmpty()) {
            return "https://" + deployment.getIngressHost();
        }
        return "http://" + deployment.getDeploymentId() + "-webserver-alb.region.elb.amazonaws.com";
    }

    @Override
    public void scale(AirflowDeployment deployment, int minWorkers, int maxWorkers) {
        log.info("Scaling ECS deployment: {} to min={}, max={}", deployment.getDeploymentId(), minWorkers, maxWorkers);

        try {
            String clusterName = getClusterName(deployment);
            String serviceName = getServiceName(deployment, "worker");

            UpdateServiceRequest request = UpdateServiceRequest.builder()
                    .cluster(clusterName)
                    .service(serviceName)
                    .desiredCount(minWorkers)
                    .build();

            ecsClient.updateService(request);
            log.info("Scaled ECS service successfully");
        } catch (Exception e) {
            log.error("Failed to scale ECS deployment: {}", deployment.getDeploymentId(), e);
            throw new DeploymentException("ECS scaling failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderType() {
        return "ecs";
    }

    // Helper methods for task definition registration

    private String registerPostgresTaskDefinition(AirflowDeployment deployment) {
        String family = getTaskDefinitionFamily(deployment, "postgres");

        MountPoint mountPoint = MountPoint.builder()
                .sourceVolume("postgres-data")
                .containerPath("/var/lib/postgresql/data")
                .build();

        RegisterTaskDefinitionRequest request = RegisterTaskDefinitionRequest.builder()
                .family(family)
                .networkMode(NetworkMode.AWSVPC)
                .requiresCompatibilities(Compatibility.FARGATE)
                .cpu("512")
                .memory("1024")
                .executionRoleArn(taskExecutionRoleArn)
                .taskRoleArn(taskRoleArn)
                .containerDefinitions(
                        ContainerDefinition.builder()
                                .name("postgres")
                                .image("postgres:16")
                                .environment(
                                        KeyValuePair.builder().name("POSTGRES_USER").value("airflow").build(),
                                        KeyValuePair.builder().name("POSTGRES_PASSWORD").value("airflow").build(),
                                        KeyValuePair.builder().name("POSTGRES_DB").value("airflow").build()
                                )
                                .mountPoints(mountPoint)
                                .portMappings(PortMapping.builder()
                                        .containerPort(5432)
                                        .protocol(TransportProtocol.TCP)
                                        .build())
                                .logConfiguration(getLogConfiguration(deployment, "postgres"))
                                .healthCheck(HealthCheck.builder()
                                        .command("CMD-SHELL", "pg_isready -U airflow")
                                        .interval(10)
                                        .timeout(5)
                                        .retries(5)
                                        .build())
                                .build()
                )
                .volumes(Volume.builder()
                        .name("postgres-data")
                        .efsVolumeConfiguration(EFSVolumeConfiguration.builder()
                                .fileSystemId(efsFileSystemId)
                                .transitEncryption(EFSTransitEncryption.ENABLED)
                                .rootDirectory("/postgres/" + deployment.getDeploymentId())
                                .build())
                        .build())
                .build();

        RegisterTaskDefinitionResponse response = ecsClient.registerTaskDefinition(request);
        return response.taskDefinition().taskDefinitionArn();
    }

    private String registerRedisTaskDefinition(AirflowDeployment deployment) {
        String family = getTaskDefinitionFamily(deployment, "redis");

        RegisterTaskDefinitionRequest request = RegisterTaskDefinitionRequest.builder()
                .family(family)
                .networkMode(NetworkMode.AWSVPC)
                .requiresCompatibilities(Compatibility.FARGATE)
                .cpu("256")
                .memory("512")
                .executionRoleArn(taskExecutionRoleArn)
                .taskRoleArn(taskRoleArn)
                .containerDefinitions(
                        ContainerDefinition.builder()
                                .name("redis")
                                .image("redis:7-alpine")
                                .command("redis-server", "--appendonly", "yes")
                                .portMappings(PortMapping.builder()
                                        .containerPort(6379)
                                        .protocol(TransportProtocol.TCP)
                                        .build())
                                .logConfiguration(getLogConfiguration(deployment, "redis"))
                                .healthCheck(HealthCheck.builder()
                                        .command("CMD", "redis-cli", "ping")
                                        .interval(10)
                                        .timeout(5)
                                        .retries(5)
                                        .build())
                                .build()
                )
                .build();

        RegisterTaskDefinitionResponse response = ecsClient.registerTaskDefinition(request);
        return response.taskDefinition().taskDefinitionArn();
    }

    private String registerSchedulerTaskDefinition(AirflowDeployment deployment) {
        String family = getTaskDefinitionFamily(deployment, "scheduler");

        RegisterTaskDefinitionRequest request = RegisterTaskDefinitionRequest.builder()
                .family(family)
                .networkMode(NetworkMode.AWSVPC)
                .requiresCompatibilities(Compatibility.FARGATE)
                .cpu(deployment.getSchedulerCpu())
                .memory(deployment.getSchedulerMemory())
                .executionRoleArn(taskExecutionRoleArn)
                .taskRoleArn(taskRoleArn)
                .containerDefinitions(
                        ContainerDefinition.builder()
                                .name("airflow-scheduler")
                                .image("apache/airflow:" + deployment.getAirflowVersion())
                                .command("scheduler")
                                .environment(getAirflowEnvironment(deployment))
                                .logConfiguration(getLogConfiguration(deployment, "scheduler"))
                                .build()
                )
                .build();

        RegisterTaskDefinitionResponse response = ecsClient.registerTaskDefinition(request);
        return response.taskDefinition().taskDefinitionArn();
    }

    private String registerWebserverTaskDefinition(AirflowDeployment deployment) {
        String family = getTaskDefinitionFamily(deployment, "webserver");

        RegisterTaskDefinitionRequest request = RegisterTaskDefinitionRequest.builder()
                .family(family)
                .networkMode(NetworkMode.AWSVPC)
                .requiresCompatibilities(Compatibility.FARGATE)
                .cpu(deployment.getWebserverCpu())
                .memory(deployment.getWebserverMemory())
                .executionRoleArn(taskExecutionRoleArn)
                .taskRoleArn(taskRoleArn)
                .containerDefinitions(
                        ContainerDefinition.builder()
                                .name("airflow-webserver")
                                .image("apache/airflow:" + deployment.getAirflowVersion())
                                .command("api-server")
                                .environment(getAirflowEnvironment(deployment))
                                .portMappings(PortMapping.builder()
                                        .containerPort(8080)
                                        .protocol(TransportProtocol.TCP)
                                        .build())
                                .logConfiguration(getLogConfiguration(deployment, "webserver"))
                                .build()
                )
                .build();

        RegisterTaskDefinitionResponse response = ecsClient.registerTaskDefinition(request);
        return response.taskDefinition().taskDefinitionArn();
    }

    private String registerWorkerTaskDefinition(AirflowDeployment deployment) {
        String family = getTaskDefinitionFamily(deployment, "worker");

        RegisterTaskDefinitionRequest request = RegisterTaskDefinitionRequest.builder()
                .family(family)
                .networkMode(NetworkMode.AWSVPC)
                .requiresCompatibilities(Compatibility.FARGATE)
                .cpu(deployment.getWorkerCpu())
                .memory(deployment.getWorkerMemory())
                .executionRoleArn(taskExecutionRoleArn)
                .taskRoleArn(taskRoleArn)
                .containerDefinitions(
                        ContainerDefinition.builder()
                                .name("airflow-worker")
                                .image("apache/airflow:" + deployment.getAirflowVersion())
                                .command("celery", "worker")
                                .environment(getAirflowEnvironment(deployment))
                                .logConfiguration(getLogConfiguration(deployment, "worker"))
                                .build()
                )
                .build();

        RegisterTaskDefinitionResponse response = ecsClient.registerTaskDefinition(request);
        return response.taskDefinition().taskDefinitionArn();
    }

    private List<KeyValuePair> getAirflowEnvironment(AirflowDeployment deployment) {
        List<KeyValuePair> env = new ArrayList<>();

        String namespaceSuffix = "." + deployment.getDeploymentId();
        String postgresHost = getServiceName(deployment, "postgres") + namespaceSuffix;
        String apiServerHost = getServiceName(deployment, "webserver") + namespaceSuffix;

        env.add(KeyValuePair.builder().name("AIRFLOW__DATABASE__SQL_ALCHEMY_CONN")
                .value("postgresql+psycopg2://airflow:airflow@" + postgresHost + ":5432/airflow").build());

        env.add(KeyValuePair.builder().name("AIRFLOW__CORE__EXECUTOR")
                .value(getExecutorConfig(deployment.getExecutorType())).build());

        env.add(KeyValuePair.builder().name("AIRFLOW__CORE__AUTH_MANAGER")
                .value("airflow.providers.fab.auth_manager.fab_auth_manager.FabAuthManager").build());
        env.add(KeyValuePair.builder().name("AIRFLOW__CORE__EXECUTION_API_SERVER_URL")
                .value("http://" + apiServerHost + ":8080/execution/").build());
        env.add(KeyValuePair.builder().name("AIRFLOW__API_AUTH__JWT_SECRET").value("airflow_jwt_secret").build());
        env.add(KeyValuePair.builder().name("AIRFLOW__API_AUTH__JWT_ISSUER").value("airflow").build());
        env.add(KeyValuePair.builder().name("AIRFLOW__SCHEDULER__ENABLE_HEALTH_CHECK").value("True").build());

        if (deployment.getExecutorType() == AirflowDeployment.ExecutorType.CELERY ||
            deployment.getExecutorType() == AirflowDeployment.ExecutorType.CELERY_KUBERNETES) {
            String redisHost = getServiceName(deployment, "redis") + namespaceSuffix;
            env.add(KeyValuePair.builder().name("AIRFLOW__CELERY__BROKER_URL")
                    .value("redis://:@" + redisHost + ":6379/0").build());
            env.add(KeyValuePair.builder().name("AIRFLOW__CELERY__RESULT_BACKEND")
                    .value("db+postgresql+psycopg2://airflow:airflow@" + postgresHost + ":5432/airflow").build());
        }

        env.add(KeyValuePair.builder().name("AIRFLOW__CORE__LOAD_EXAMPLES").value("False").build());

        return env;
    }

    private String getExecutorConfig(AirflowDeployment.ExecutorType executorType) {
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

    private LogConfiguration getLogConfiguration(AirflowDeployment deployment, String component) {
        return LogConfiguration.builder()
                .logDriver(LogDriver.AWSLOGS)
                .options(Map.of(
                        "awslogs-group", "/ecs/managed-airflow/" + deployment.getDeploymentId(),
                        "awslogs-region", "us-east-1",
                        "awslogs-stream-prefix", component
                ))
                .build();
    }

    private void createSchedulerService(AirflowDeployment deployment, String clusterName, String taskDefinition) {
        String serviceName = getServiceName(deployment, "scheduler");

        CreateServiceRequest request = CreateServiceRequest.builder()
                .cluster(clusterName)
                .serviceName(serviceName)
                .taskDefinition(taskDefinition)
                .desiredCount(1)
                .launchType(LaunchType.FARGATE)
                .networkConfiguration(NetworkConfiguration.builder()
                        .awsvpcConfiguration(AwsVpcConfiguration.builder()
                                .subnets(subnetIds)
                                .securityGroups(securityGroupIds)
                                .assignPublicIp(AssignPublicIp.ENABLED)
                                .build())
                        .build())
                .build();

        ecsClient.createService(request);
        log.info("Created scheduler service: {}", serviceName);
    }

    private void createWebserverService(AirflowDeployment deployment, String clusterName, String taskDefinition) {
        String serviceName = getServiceName(deployment, "webserver");

        CreateServiceRequest request = CreateServiceRequest.builder()
                .cluster(clusterName)
                .serviceName(serviceName)
                .taskDefinition(taskDefinition)
                .desiredCount(1)
                .launchType(LaunchType.FARGATE)
                .networkConfiguration(NetworkConfiguration.builder()
                        .awsvpcConfiguration(AwsVpcConfiguration.builder()
                                .subnets(subnetIds)
                                .securityGroups(securityGroupIds)
                                .assignPublicIp(AssignPublicIp.ENABLED)
                                .build())
                        .build())
                .build();

        ecsClient.createService(request);
        log.info("Created webserver service: {}", serviceName);
    }

    private void createWorkerService(AirflowDeployment deployment, String clusterName, String taskDefinition) {
        String serviceName = getServiceName(deployment, "worker");

        CreateServiceRequest request = CreateServiceRequest.builder()
                .cluster(clusterName)
                .serviceName(serviceName)
                .taskDefinition(taskDefinition)
                .desiredCount(deployment.getMinWorkers())
                .launchType(LaunchType.FARGATE)
                .networkConfiguration(NetworkConfiguration.builder()
                        .awsvpcConfiguration(AwsVpcConfiguration.builder()
                                .subnets(subnetIds)
                                .securityGroups(securityGroupIds)
                                .assignPublicIp(AssignPublicIp.ENABLED)
                                .build())
                        .build())
                .build();

        ecsClient.createService(request);
        log.info("Created worker service: {}", serviceName);
    }

    private void createPostgresService(AirflowDeployment deployment, String clusterName, String taskDefinition) {
        String serviceName = getServiceName(deployment, "postgres");

        CreateServiceRequest request = CreateServiceRequest.builder()
                .cluster(clusterName)
                .serviceName(serviceName)
                .taskDefinition(taskDefinition)
                .desiredCount(1)
                .launchType(LaunchType.FARGATE)
                .networkConfiguration(NetworkConfiguration.builder()
                        .awsvpcConfiguration(AwsVpcConfiguration.builder()
                                .subnets(subnetIds)
                                .securityGroups(securityGroupIds)
                                .assignPublicIp(AssignPublicIp.ENABLED)
                                .build())
                        .build())
                .serviceRegistries(ServiceRegistry.builder()
                        .registryArn("") // TODO: Configure service discovery
                        .build())
                .build();

        ecsClient.createService(request);
        log.info("Created postgres service: {}", serviceName);
    }

    private void createRedisService(AirflowDeployment deployment, String clusterName, String taskDefinition) {
        String serviceName = getServiceName(deployment, "redis");

        CreateServiceRequest request = CreateServiceRequest.builder()
                .cluster(clusterName)
                .serviceName(serviceName)
                .taskDefinition(taskDefinition)
                .desiredCount(1)
                .launchType(LaunchType.FARGATE)
                .networkConfiguration(NetworkConfiguration.builder()
                        .awsvpcConfiguration(AwsVpcConfiguration.builder()
                                .subnets(subnetIds)
                                .securityGroups(securityGroupIds)
                                .assignPublicIp(AssignPublicIp.ENABLED)
                                .build())
                        .build())
                .build();

        ecsClient.createService(request);
        log.info("Created redis service: {}", serviceName);
    }

    private void waitForServiceStable(String clusterName, String serviceName) {
        log.info("Waiting for service {} to be stable...", serviceName);

        try {
            int maxAttempts = 60; // 5 minutes max (5 second intervals)
            int attempt = 0;

            while (attempt < maxAttempts) {
                DescribeServicesRequest request = DescribeServicesRequest.builder()
                        .cluster(clusterName)
                        .services(serviceName)
                        .build();

                DescribeServicesResponse response = ecsClient.describeServices(request);

                if (!response.services().isEmpty()) {
                    Service service = response.services().get(0);
                    if (service.runningCount() > 0 && service.runningCount().equals(service.desiredCount())) {
                        log.info("Service {} is stable", serviceName);
                        return;
                    }
                }

                Thread.sleep(5000);
                attempt++;
            }

            log.warn("Service {} did not become stable within timeout", serviceName);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting for service: {}", serviceName, e);
        }
    }

    private void updateService(String clusterName, String serviceName, String taskDefinition) {
        UpdateServiceRequest request = UpdateServiceRequest.builder()
                .cluster(clusterName)
                .service(serviceName)
                .taskDefinition(taskDefinition)
                .forceNewDeployment(true)
                .build();

        ecsClient.updateService(request);
        log.info("Updated service: {}", serviceName);
    }

    private void deleteService(String clusterName, String serviceName) {
        try {
            DeleteServiceRequest request = DeleteServiceRequest.builder()
                    .cluster(clusterName)
                    .service(serviceName)
                    .force(true)
                    .build();

            ecsClient.deleteService(request);
            log.info("Deleted service: {}", serviceName);
        } catch (ServiceNotFoundException e) {
            log.info("Service not found, skipping: {}", serviceName);
        }
    }

    private void deregisterTaskDefinition(String family) {
        try {
            // List all revisions of this task definition family
            ListTaskDefinitionsRequest listRequest = ListTaskDefinitionsRequest.builder()
                    .familyPrefix(family)
                    .build();

            ListTaskDefinitionsResponse listResponse = ecsClient.listTaskDefinitions(listRequest);

            // Deregister each revision
            for (String taskDefArn : listResponse.taskDefinitionArns()) {
                DeregisterTaskDefinitionRequest request = DeregisterTaskDefinitionRequest.builder()
                        .taskDefinition(taskDefArn)
                        .build();

                ecsClient.deregisterTaskDefinition(request);
            }

            log.info("Deregistered task definition family: {}", family);
        } catch (Exception e) {
            log.warn("Failed to deregister task definition family: {}", family, e);
        }
    }

    private String getClusterName(AirflowDeployment deployment) {
        return clusterPrefix + "-" + deployment.getTenant().getTenantId();
    }

    private String getServiceName(AirflowDeployment deployment, String component) {
        return deployment.getDeploymentId() + "-" + component;
    }

    private String getTaskDefinitionFamily(AirflowDeployment deployment, String component) {
        return "airflow-" + deployment.getDeploymentId() + "-" + component;
    }
}

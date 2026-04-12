# ECS Implementation for Managed Airflow Platform

## Overview

The Managed Airflow Platform supports AWS ECS (Elastic Container Service) as an alternative deployment target alongside Kubernetes. This implementation provides a lighter-weight, more cost-effective option for test and non-Kubernetes environments.

## Architecture

The implementation uses a **provider abstraction pattern** that allows the platform to support multiple deployment targets:

```
┌─────────────────────────────────┐
│   AirflowDeploymentService      │
│   (Cloud-agnostic orchestration)│
└─────────────┬───────────────────┘
              │
              ├─── DeploymentProvider (interface)
              │         │
              │         ├─── HelmDeploymentProvider (Kubernetes)
              │         └─── ECSDeploymentProvider (AWS ECS)
              │
              └─── CloudProvider (interface)
                        │
                        ├─── KubernetesCloudProvider
                        └─── ECSCloudProvider
```

## Key Components

### 1. Provider Abstractions

**CloudProvider Interface** (`provider/CloudProvider.java`)
- Manages cloud-specific resources (namespaces, clusters, secrets)
- Abstracts differences between Kubernetes and ECS

**DeploymentProvider Interface** (`provider/DeploymentProvider.java`)
- Handles deployment lifecycle (deploy, upgrade, uninstall, scale)
- Abstracts differences between Helm and ECS task definitions

### 2. Kubernetes Implementations

**KubernetesCloudProvider** (`provider/impl/KubernetesCloudProvider.java`)
- Wraps existing `KubernetesService`
- Manages Kubernetes namespaces and secrets

**HelmDeploymentProvider** (`provider/impl/HelmDeploymentProvider.java`)
- Wraps existing `HelmService`
- Manages Helm chart deployments

### 3. ECS Implementations

**ECSCloudProvider** (`provider/impl/ECSCloudProvider.java`)
- Manages ECS clusters (one per tenant)
- Manages AWS Secrets Manager
- Handles cluster lifecycle

**ECSDeploymentProvider** (`provider/impl/ECSDeploymentProvider.java`)
- Creates and manages ECS task definitions for:
  - Airflow Scheduler
  - Airflow Webserver
  - Airflow Workers (for CeleryExecutor)
- Creates and manages ECS services
- Configures networking and load balancing

**ECSScalingManager** (`service/ECSScalingManager.java`)
- Provides KEDA-like auto-scaling for ECS
- Scales based on CPU (70%) and memory (80%) utilization
- Configures AWS Application Auto Scaling policies

### 4. Configuration

**AWSConfig** (`config/AWSConfig.java`)
- Configures AWS SDK clients (conditional on `deployment.provider=ecs`)
- Creates beans for:
  - EcsClient
  - SecretsManagerClient
  - ElasticLoadBalancingV2Client
  - ApplicationAutoScalingClient

**application.yml** (updated)
- New `ecs` profile for ECS deployments
- Configuration properties for:
  - AWS region
  - ECS cluster settings
  - IAM role ARNs
  - VPC/networking settings
  - Database and Redis endpoints

### 5. Infrastructure as Code

**CloudFormation Template** (`infrastructure/ecs/cloudformation-ecs-infrastructure.yaml`)
- Complete infrastructure setup including:
  - VPC with public subnets
  - Security groups
  - RDS PostgreSQL (metadata database)
  - ElastiCache Redis (Celery broker)
  - IAM roles for ECS tasks
  - CloudWatch log groups

**Terraform Templates** (`infrastructure/ecs/terraform/`)
- `main.tf` - Main infrastructure resources
- `variables.tf` - Configurable parameters
- `outputs.tf` - Outputs for application configuration

## Feature Comparison

| Feature | Kubernetes | ECS |
|---------|-----------|-----|
| Multi-tenancy | Namespaces | Clusters |
| Task definitions | Helm charts | ECS task definitions |
| Auto-scaling | KEDA | Application Auto Scaling |
| Secret management | Kubernetes Secrets | AWS Secrets Manager |
| Logging | Various | CloudWatch Logs |
| Load balancing | Ingress | Application Load Balancer |
| Executors | All types | All types |
| Activation | `deployment.provider=kubernetes` (default) | `deployment.provider=ecs` |

## How It Works

### Deployment Flow

1. **Tenant Creation**
   - CloudProvider creates isolated namespace/cluster
   - For ECS: Creates dedicated ECS cluster
   - For K8s: Creates Kubernetes namespace

2. **Airflow Deployment**
   - DeploymentProvider registers task definitions/Helm charts
   - Creates services/deployments for:
     - Scheduler (1 instance)
     - Webserver (1 instance)
     - Workers (min to max, auto-scaled)

3. **Auto-Scaling**
   - ECS: Application Auto Scaling with target tracking
   - K8s: KEDA with queue-depth scaling

4. **Updates**
   - DeploymentProvider updates task definitions/Helm values
   - Triggers rolling updates of services

5. **Deletion**
   - DeploymentProvider removes services
   - CloudProvider removes namespace/cluster

## Files Created/Modified

### New Files

**Provider Interfaces:**
- `control-plane/src/main/java/com/airflow/platform/provider/CloudProvider.java`
- `control-plane/src/main/java/com/airflow/platform/provider/DeploymentProvider.java`

**Kubernetes Providers:**
- `control-plane/src/main/java/com/airflow/platform/provider/impl/KubernetesCloudProvider.java`
- `control-plane/src/main/java/com/airflow/platform/provider/impl/HelmDeploymentProvider.java`

**ECS Providers:**
- `control-plane/src/main/java/com/airflow/platform/provider/impl/ECSCloudProvider.java`
- `control-plane/src/main/java/com/airflow/platform/provider/impl/ECSDeploymentProvider.java`
- `control-plane/src/main/java/com/airflow/platform/service/ECSScalingManager.java`

**Configuration:**
- `control-plane/src/main/java/com/airflow/platform/config/AWSConfig.java`

**Infrastructure:**
- `infrastructure/ecs/README.md`
- `infrastructure/ecs/cloudformation-ecs-infrastructure.yaml`
- `infrastructure/ecs/terraform/main.tf`
- `infrastructure/ecs/terraform/variables.tf`
- `infrastructure/ecs/terraform/outputs.tf`

### Modified Files

- `control-plane/src/main/java/com/airflow/platform/service/AirflowDeploymentService.java`
  - Now uses `DeploymentProvider` instead of `HelmService` directly
  - Supports ECS auto-scaling

- `control-plane/src/main/java/com/airflow/platform/service/TenantService.java`
  - Now uses `CloudProvider` instead of `KubernetesService` directly

- `control-plane/src/main/resources/application.yml`
  - Added `ecs` profile configuration

- `control-plane/pom.xml`
  - Added AWS SDK v2 dependencies

## Usage

### Running with Kubernetes (default)

```bash
mvn spring-boot:run
# or
java -jar target/managed-airflow-control-plane-0.0.1-SNAPSHOT.jar
```

### Running with ECS

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=ecs
# or
java -jar target/managed-airflow-control-plane-0.0.1-SNAPSHOT.jar --spring.profiles.active=ecs
```

### Setting up ECS Infrastructure

See `infrastructure/ecs/README.md` for detailed instructions.

## Benefits of This Implementation

1. **Flexibility**: Switch between Kubernetes and ECS with a single configuration change
2. **Cost-effective**: ECS with Fargate is cheaper for test/dev environments
3. **Simplified operations**: No Kubernetes cluster management required for ECS
4. **AWS-native**: Leverages AWS managed services (RDS, ElastiCache, Secrets Manager)
5. **Auto-scaling**: Both platforms support automatic worker scaling
6. **Production-ready**: Both implementations support production workloads

## Testing Recommendations

1. **Unit tests**: Add tests for provider implementations
2. **Integration tests**: Test with both Kubernetes and ECS profiles
3. **End-to-end tests**: Deploy sample DAGs and verify execution
4. **Scale tests**: Verify auto-scaling works correctly
5. **Failover tests**: Test service recovery and rolling updates

## Future Enhancements

1. **Additional providers**: Azure Container Instances, Google Cloud Run
2. **Hybrid deployments**: Support mixed Kubernetes + ECS deployments
3. **Advanced networking**: Custom VPC configurations, private endpoints
4. **Monitoring**: Enhanced CloudWatch/Prometheus integration
5. **Cost optimization**: Spot instances, reserved capacity
6. **Blue-green deployments**: Zero-downtime updates
7. **Disaster recovery**: Automated backups and restore

## Known Limitations

1. **KubernetesExecutor**: Works on ECS but less efficient than on Kubernetes
2. **Custom plugins**: Require custom Docker images
3. **Networking**: ALB configuration currently manual
4. **Multi-region**: Currently single-region deployments only

## Conclusion

The ECS implementation provides a production-ready alternative to Kubernetes for deploying Apache Airflow, making the Managed Airflow Platform truly multi-cloud and flexible. The provider abstraction pattern ensures the codebase remains maintainable while supporting multiple deployment targets.

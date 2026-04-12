# ECS Deployment Architecture

## 1. Overview

The ECS (Elastic Container Service) deployment option provides a **serverless, fully containerized** approach to running Apache Airflow. This implementation uses AWS Fargate to run all components (including PostgreSQL and Redis) as containers, eliminating the need for managed database services like RDS and ElastiCache.

### Key Characteristics

- **Platform**: AWS ECS Fargate (serverless containers)
- **Multi-Tenancy**: One ECS cluster per tenant
- **Database**: Containerized PostgreSQL with EFS persistence
- **Message Broker**: Containerized Redis (for Celery executor)
- **Auto-Scaling**: AWS Application Auto Scaling
- **Cost**: ~$45-50/month per tenant (optimized configuration)
- **Best For**: Test/staging environments, small-medium production workloads

### Architecture Philosophy

This implementation provides a **middle ground** between the simplicity of EC2 Docker Compose and the sophistication of Kubernetes:
- Simpler than Kubernetes (no cluster management)
- More scalable than EC2 (auto-scaling, self-healing)
- Cost-effective (no managed service charges)
- Container-based (consistent with other deployment options)

## 2. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    AWS Cloud (Per Tenant)                        │
│                                                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │              ECS Cluster: managed-airflow-{tenant-id}      │ │
│  │                                                             │ │
│  │  ┌──────────────────┐        ┌──────────────────┐         │ │
│  │  │  PostgreSQL      │        │  Redis           │         │ │
│  │  │  Fargate Task    │        │  Fargate Task    │         │ │
│  │  │  (0.25 vCPU)     │        │  (0.25 vCPU)     │         │ │
│  │  │  Port: 5432      │        │  Port: 6379      │         │ │
│  │  └────────┬─────────┘        └──────────────────┘         │ │
│  │           │                                                 │ │
│  │           │ EFS Volume                                      │ │
│  │           ↓                                                 │ │
│  │  ┌──────────────────┐                                      │ │
│  │  │ EFS File System  │                                      │ │
│  │  │ /postgres/...    │                                      │ │
│  │  └──────────────────┘                                      │ │
│  │                                                             │ │
│  │  ┌──────────────────────────────────────────────────────┐ │ │
│  │  │           Airflow Components (Fargate Tasks)         │ │ │
│  │  │                                                       │ │ │
│  │  │  ┌────────────┐  ┌────────────┐  ┌────────────┐    │ │ │
│  │  │  │ Scheduler  │  │ Webserver  │  │  Workers   │    │ │ │
│  │  │  │ (0.5 vCPU) │  │ (0.25 vCPU)│  │ (0.5 vCPU) │    │ │ │
│  │  │  │            │  │ Port: 8080 │  │ (1-N tasks)│    │ │ │
│  │  │  └────────────┘  └─────┬──────┘  └────────────┘    │ │ │
│  │  │                         │                            │ │ │
│  │  └─────────────────────────┼────────────────────────────┘ │ │
│  │                            │                               │ │
│  └────────────────────────────┼───────────────────────────────┘ │
│                               │                                 │
│                   ┌───────────▼────────────┐                    │
│                   │  Application Load      │                    │
│                   │  Balancer (ALB)        │                    │
│                   │  Port: 80/443          │                    │
│                   └───────────┬────────────┘                    │
└───────────────────────────────┼──────────────────────────────────┘
                                │
                                ▼
                          User Access
                     (https://airflow.example.com)


Control Plane Integration:
┌─────────────────────────────────────────────────────────────┐
│  Spring Boot Application (Control Plane)                    │
│                                                              │
│  ┌──────────────────┐    ┌──────────────────┐             │
│  │ ECSCloudProvider │───▶│ AWS ECS API      │             │
│  │                  │    │ - Create Cluster  │             │
│  │ - createNamespace│    │ - Delete Cluster  │             │
│  │ - deleteNamespace│    │                   │             │
│  │ - createSecret   │    └──────────────────┘             │
│  └──────────────────┘                                       │
│                                                              │
│  ┌──────────────────────┐  ┌──────────────────┐           │
│  │ ECSDeploymentProvider│─▶│ AWS ECS API      │           │
│  │                      │  │ - Task Definitions│           │
│  │ - deploy()           │  │ - Services        │           │
│  │ - upgrade()          │  │ - Tasks           │           │
│  │ - uninstall()        │  └──────────────────┘           │
│  │ - scale()            │                                   │
│  └──────────────────────┘                                   │
│                                                              │
│  ┌──────────────────────┐  ┌──────────────────┐           │
│  │ ECSScalingManager    │─▶│ Application      │           │
│  │                      │  │ Auto Scaling API  │           │
│  │ - configureAutoScale │  │ - Target Tracking │           │
│  │ - updateAutoScale    │  │ - CPU/Memory      │           │
│  │ - removeAutoScale    │  └──────────────────┘           │
│  └──────────────────────┘                                   │
└─────────────────────────────────────────────────────────────┘
```

## 3. Key Components

### 3.1 ECSCloudProvider

**Location**: `control-plane/src/main/java/com/airflow/platform/provider/impl/ECSCloudProvider.java`

**Purpose**: Manages ECS clusters and AWS Secrets Manager for tenant isolation.

**Responsibilities**:
- **Cluster Management**: Creates one ECS cluster per tenant
- **Secret Management**: Stores credentials in AWS Secrets Manager
- **Tenant Isolation**: Each tenant has a dedicated cluster
- **Service Discovery**: Configures ECS Service Connect/Service Discovery

**Key Methods**:
```java
// Create ECS cluster for tenant
public void createNamespace(Tenant tenant)

// Delete ECS cluster and all services
public void deleteNamespace(Tenant tenant)

// Check if cluster exists
public boolean namespaceExists(String namespace)

// Store secrets in AWS Secrets Manager
public void createSecret(String namespace, String secretName, Map<String, String> data)
```

**Implementation Details**:
- Uses AWS ECS SDK v2 (`software.amazon.awssdk.services.ecs`)
- Configures Fargate capacity providers (FARGATE and FARGATE_SPOT)
- Adds tenant tags for resource tracking
- Cluster naming: `{cluster-prefix}-{tenant-id}`

### 3.2 ECSDeploymentProvider

**Location**: `control-plane/src/main/java/com/airflow/platform/provider/impl/ECSDeploymentProvider.java`

**Purpose**: Manages the complete lifecycle of Airflow deployments on ECS.

**Responsibilities**:
- **Task Definition Management**: Creates/updates task definitions for all components
- **Service Orchestration**: Deploys and manages ECS services
- **Container Configuration**: Sets up PostgreSQL, Redis, and Airflow containers
- **Health Monitoring**: Waits for services to become stable
- **Load Balancer Integration**: Configures ALB for webserver access

**Key Methods**:
```java
// Deploy complete Airflow stack
public void deploy(AirflowDeployment deployment)

// Upgrade existing deployment
public void upgrade(AirflowDeployment deployment)

// Remove all resources
public void uninstall(AirflowDeployment deployment)

// Scale worker count
public void scale(AirflowDeployment deployment, int minWorkers, int maxWorkers)

// Get deployment status
public String getDeploymentStatus(AirflowDeployment deployment)
```

**Deployment Flow**:
1. Register PostgreSQL task definition → Create PostgreSQL service
2. Register Redis task definition (if Celery) → Create Redis service
3. Wait for PostgreSQL to be stable
4. Register Airflow task definitions → Create Airflow services
5. Configure auto-scaling for workers

### 3.3 ECSScalingManager

**Location**: `control-plane/src/main/java/com/airflow/platform/service/ECSScalingManager.java`

**Purpose**: Manages auto-scaling for ECS services (equivalent to KEDA for Kubernetes).

**Responsibilities**:
- **Auto-Scaling Configuration**: Sets up target tracking policies
- **Resource Monitoring**: Tracks CPU and memory utilization
- **Dynamic Scaling**: Adjusts worker count based on load
- **Scaling Policies**: Manages scale-in and scale-out behavior

**Key Methods**:
```java
// Configure auto-scaling for workers
public void configureAutoScaling(AirflowDeployment deployment)

// Update scaling parameters
public void updateAutoScaling(AirflowDeployment deployment, int minWorkers, int maxWorkers)

// Remove auto-scaling configuration
public void removeAutoScaling(AirflowDeployment deployment)
```

**Scaling Policies**:
- **CPU-based**: Target 70% CPU utilization
- **Memory-based**: Target 80% memory utilization
- **Scale-out cooldown**: 60 seconds
- **Scale-in cooldown**: 300 seconds (5 minutes)

## 4. Multi-Tenancy Model

### Cluster-Per-Tenant Isolation

```
Tenant A                          Tenant B
┌─────────────────────┐          ┌─────────────────────┐
│ Cluster: airflow-A  │          │ Cluster: airflow-B  │
│                     │          │                     │
│ - PostgreSQL        │          │ - PostgreSQL        │
│ - Redis             │          │ - Redis             │
│ - Scheduler         │          │ - Scheduler         │
│ - Webserver         │          │ - Webserver         │
│ - Workers (N)       │          │ - Workers (M)       │
└─────────────────────┘          └─────────────────────┘
        │                                │
        ├─ Isolated network              ├─ Isolated network
        ├─ Dedicated EFS volume          ├─ Dedicated EFS volume
        └─ Separate secrets              └─ Separate secrets
```

### Benefits of Cluster-Per-Tenant:
- **Strong Isolation**: Complete separation between tenants
- **Independent Scaling**: Each tenant scales independently
- **Resource Limits**: Per-tenant resource quotas
- **Failure Isolation**: One tenant's issues don't affect others
- **Custom Configuration**: Tenant-specific settings

### Naming Conventions:
- **Cluster**: `{cluster-prefix}-{tenant-id}` (e.g., `managed-airflow-acme-corp`)
- **Service**: `{deployment-id}-{component}` (e.g., `prod-airflow-123-webserver`)
- **Task Definition**: `airflow-{deployment-id}-{component}`
- **Secrets**: `{cluster-name}/{secret-name}`
- **EFS Path**: `/postgres/{deployment-id}`

## 5. Containerized Services

### 5.1 PostgreSQL Container

**Image**: `postgres:13`

**Task Definition**:
```json
{
  "family": "airflow-{deployment-id}-postgres",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512",      // 0.5 vCPU (can be reduced to 256 for cost savings)
  "memory": "1024",  // 1 GB (can be reduced to 512 for cost savings)
  "containerDefinitions": [{
    "name": "postgres",
    "image": "postgres:13",
    "portMappings": [{"containerPort": 5432}],
    "environment": [
      {"name": "POSTGRES_USER", "value": "airflow"},
      {"name": "POSTGRES_PASSWORD", "value": "airflow"},
      {"name": "POSTGRES_DB", "value": "airflow"}
    ],
    "mountPoints": [{
      "sourceVolume": "postgres-data",
      "containerPath": "/var/lib/postgresql/data"
    }],
    "healthCheck": {
      "command": ["CMD-SHELL", "pg_isready -U airflow"],
      "interval": 10,
      "timeout": 5,
      "retries": 5
    }
  }],
  "volumes": [{
    "name": "postgres-data",
    "efsVolumeConfiguration": {
      "fileSystemId": "fs-xxxxxxxxx",
      "transitEncryption": "ENABLED",
      "rootDirectory": "/postgres/{deployment-id}"
    }
  }]
}
```

**Persistence**:
- Data stored on AWS EFS (Elastic File System)
- Encrypted in transit (TLS) and at rest
- Survives container restarts
- Backed up via EFS snapshots

**Connection String**: `postgres.{deployment-id}:5432` (via Service Discovery)

### 5.2 Redis Container

**Image**: `redis:7-alpine`

**Task Definition**:
```json
{
  "family": "airflow-{deployment-id}-redis",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "256",     // 0.25 vCPU
  "memory": "512",  // 512 MB (can be reduced to 256 for cost savings)
  "containerDefinitions": [{
    "name": "redis",
    "image": "redis:7-alpine",
    "command": ["redis-server", "--appendonly", "yes"],
    "portMappings": [{"containerPort": 6379}],
    "healthCheck": {
      "command": ["CMD", "redis-cli", "ping"],
      "interval": 10,
      "timeout": 5,
      "retries": 5
    }
  }]
}
```

**Persistence**:
- AOF (Append-Only File) persistence enabled
- In-memory data with disk backup
- Suitable for message broker workloads

**Connection String**: `redis.{deployment-id}:6379` (via Service Discovery)

### 5.3 Airflow Scheduler

**Image**: `apache/airflow:{version}`

**Task Definition**:
```json
{
  "family": "airflow-{deployment-id}-scheduler",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "1024",    // 1 vCPU (configurable)
  "memory": "2048", // 2 GB (configurable)
  "containerDefinitions": [{
    "name": "airflow-scheduler",
    "image": "apache/airflow:3.1.8",
    "command": ["scheduler"],
    "environment": [
      // See section 5.6 for full environment variables
    ]
  }]
}
```

**Configuration**: See ECSDeploymentProvider.java:319-343

### 5.4 Airflow Webserver

**Image**: `apache/airflow:{version}`

**Task Definition**:
```json
{
  "family": "airflow-{deployment-id}-webserver",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512",     // 0.5 vCPU (configurable)
  "memory": "1024", // 1 GB (configurable)
  "containerDefinitions": [{
    "name": "airflow-webserver",
    "image": "apache/airflow:3.1.8",
    "command": ["webserver"],
    "portMappings": [{"containerPort": 8080}],
    "environment": [
      // See section 5.6 for full environment variables
    ]
  }]
}
```

**Configuration**: See ECSDeploymentProvider.java:345-373

### 5.5 Airflow Workers

**Image**: `apache/airflow:{version}`

**Task Definition**:
```json
{
  "family": "airflow-{deployment-id}-worker",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "1024",    // 1 vCPU (configurable)
  "memory": "2048", // 2 GB (configurable)
  "containerDefinitions": [{
    "name": "airflow-worker",
    "image": "apache/airflow:3.1.8",
    "command": ["celery", "worker"],
    "environment": [
      // See section 5.6 for full environment variables
    ]
  }]
}
```

**Scaling**:
- Min workers: 1 (configurable)
- Max workers: 10 (configurable)
- Auto-scales based on CPU/memory utilization

**Configuration**: See ECSDeploymentProvider.java:375-399

### 5.6 Environment Variables

**Common Environment Variables** (ECSDeploymentProvider.java:401-431):

```bash
# Database connection (containerized PostgreSQL)
AIRFLOW__DATABASE__SQL_ALCHEMY_CONN=postgresql://airflow:airflow@postgres.{deployment-id}:5432/airflow

# Executor configuration
AIRFLOW__CORE__EXECUTOR=CeleryExecutor  # or LocalExecutor

# Redis connection (for Celery, containerized Redis)
AIRFLOW__CELERY__BROKER_URL=redis://redis.{deployment-id}:6379/0
AIRFLOW__CELERY__RESULT_BACKEND=db+postgresql://airflow:airflow@postgres.{deployment-id}:5432/airflow

# Webserver configuration
AIRFLOW__WEBSERVER__BASE_URL=http://localhost:8080

# Disable example DAGs
AIRFLOW__CORE__LOAD_EXAMPLES=False
```

## 6. AWS EFS Integration

### EFS Architecture

```
┌─────────────────────────────────────────────────────────┐
│              EFS File System (fs-xxxxxxxxx)              │
│                    (Encrypted at rest)                   │
│                                                           │
│  /postgres/                                               │
│  ├── deployment-1/    ← PostgreSQL data for deployment 1 │
│  │   ├── base/                                            │
│  │   ├── global/                                          │
│  │   ├── pg_wal/                                          │
│  │   └── ...                                              │
│  ├── deployment-2/    ← PostgreSQL data for deployment 2 │
│  │   └── ...                                              │
│  └── deployment-N/                                        │
│      └── ...                                              │
└─────────────────────────────────────────────────────────┘
            ↑                    ↑
            │                    │
    ┌───────┴────────┐  ┌───────┴────────┐
    │ Mount Target 1 │  │ Mount Target 2 │
    │  (AZ-1)        │  │  (AZ-2)        │
    └────────────────┘  └────────────────┘
            ↑                    ↑
            │                    │
    ┌───────┴────────┐  ┌───────┴────────┐
    │ PostgreSQL     │  │ PostgreSQL     │
    │ Container      │  │ Container      │
    │ (Subnet-1)     │  │ (Subnet-2)     │
    └────────────────┘  └────────────────┘
```

### EFS Configuration

**Terraform** (infrastructure/ecs/terraform/main.tf:126-167):

```hcl
# EFS File System
resource "aws_efs_file_system" "airflow" {
  creation_token = "${var.environment_name}-efs"
  encrypted      = true

  lifecycle_policy {
    transition_to_ia = "AFTER_30_DAYS"  # Cost optimization
  }
}

# Mount Targets (one per AZ)
resource "aws_efs_mount_target" "airflow" {
  count           = 2
  file_system_id  = aws_efs_file_system.airflow.id
  subnet_id       = aws_subnet.public[count.index].id
  security_groups = [aws_security_group.efs.id]
}

# Access Point for PostgreSQL
resource "aws_efs_access_point" "postgres" {
  file_system_id = aws_efs_file_system.airflow.id

  posix_user {
    gid = 999  # postgres user
    uid = 999
  }

  root_directory {
    path = "/postgres"
    creation_info {
      owner_gid   = 999
      owner_uid   = 999
      permissions = "755"
    }
  }
}
```

### EFS Security

**Security Group** (infrastructure/ecs/terraform/main.tf:102-124):

```hcl
resource "aws_security_group" "efs" {
  name        = "${var.environment_name}-efs-sg"
  description = "Security group for EFS"
  vpc_id      = aws_vpc.main.id

  # Allow NFS traffic from ECS tasks
  ingress {
    from_port       = 2049
    to_port         = 2049
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs.id]
  }
}
```

### Benefits:
- **Persistent Storage**: Data survives container restarts
- **Multi-AZ**: High availability across availability zones
- **Encryption**: In-transit (TLS) and at-rest encryption
- **Scalable**: Automatically grows with data
- **Shared Access**: Multiple containers can access same file system

### Limitations:
- **Performance**: Lower than RDS (suitable for small-medium workloads)
- **Throughput**: Depends on EFS throughput mode (Bursting vs Provisioned)
- **Cost**: $0.30/GB-month (standard storage)

## 7. Service Discovery

### ECS Service Connect

ECS uses **Service Connect** or **Cloud Map** for container-to-container communication.

**DNS Resolution**:
```
Service Name: postgres
Namespace: {deployment-id}
Full DNS: postgres.{deployment-id}
```

**Configuration** (ECSDeploymentProvider.java:541-543):

```java
.serviceRegistries(ServiceRegistry.builder()
    .registryArn("") // TODO: Configure service discovery
    .build())
```

**Network Flow**:
```
Scheduler Container
    ↓
    Resolves: postgres.{deployment-id}
    ↓
    Connects to: 10.0.1.45:5432 (PostgreSQL container IP)
```

### Benefits:
- **No hardcoded IPs**: Containers discover each other via DNS
- **Automatic updates**: DNS records updated when containers restart
- **Health checks**: Only healthy tasks receive traffic
- **Load balancing**: Distributes traffic across multiple tasks

## 8. Deployment Lifecycle

### 8.1 Infrastructure Setup (One-Time)

```bash
cd infrastructure/ecs/terraform

# Initialize Terraform
terraform init

# Create VPC, ECS, EFS, IAM roles
terraform apply
```

**Resources Created**:
- VPC with 2 public subnets (2 AZs)
- Internet Gateway and route tables
- ECS security group (ports 8080, 5555)
- EFS security group (port 2049)
- EFS file system with mount targets
- EFS access point for PostgreSQL
- IAM roles (task execution, task role)
- CloudWatch log group

**Output** (terraform output configuration_for_application_yml):
```json
{
  "aws_region": "us-east-1",
  "ecs_cluster_prefix": "managed-airflow",
  "task_execution_role_arn": "arn:aws:iam::ACCOUNT:role/...",
  "task_role_arn": "arn:aws:iam::ACCOUNT:role/...",
  "efs_file_system_id": "fs-12345678",
  "efs_access_point_id": "fsap-87654321",
  "subnet_ids": ["subnet-xxx", "subnet-yyy"],
  "security_group_ids": ["sg-zzz"]
}
```

### 8.2 Tenant Creation

**API request** (real path: `POST /api/v1/tenants`, **ADMIN** JWT required; `tenantId` is generated from `name`):
```bash
POST /api/v1/tenants
{
  "name": "ACME Corporation",
  "email": "admin@acme.example.com",
  "organization": "ACME Corporation",
  "cloudProvider": "AWS",
  "clusterName": "managed-airflow",
  "region": "us-east-1"
}
```

**Actions** (ECSCloudProvider.java:39-62):
1. Create ECS cluster: `managed-airflow-acme-corp`
2. Configure Fargate capacity providers
3. Add tenant tags
4. Create namespace secret in AWS Secrets Manager

**Time**: ~30 seconds

### 8.3 Airflow Deployment

**API request** (`POST /api/v1/deployments`, authenticated; **`deploymentId` is generated** from `name`):
```bash
POST /api/v1/deployments
{
  "tenantId": "acme-corp",
  "name": "prod-airflow",
  "description": "Production",
  "airflowVersion": "3.1.8",
  "executorType": "CELERY",
  "schedulerCpu": "1000m",
  "schedulerMemory": "2Gi",
  "webserverCpu": "500m",
  "webserverMemory": "1Gi",
  "workerCpu": "1000m",
  "workerMemory": "2Gi",
  "minWorkers": 1,
  "maxWorkers": 5
}
```

**Deployment Steps** (ECSDeploymentProvider.java:53-100):

1. **Register PostgreSQL Task Definition** (lines 231-282)
   - Configure 0.5 vCPU, 1 GB memory
   - Mount EFS volume to `/var/lib/postgresql/data`
   - Set environment variables (POSTGRES_USER, POSTGRES_PASSWORD, POSTGRES_DB)
   - Configure health check

2. **Create PostgreSQL Service** (lines 525-548)
   - Deploy 1 task (always running)
   - Configure networking (subnets, security groups)
   - Set up service discovery

3. **Register Redis Task Definition** (if Celery) (lines 284-317)
   - Configure 0.25 vCPU, 512 MB memory
   - Enable AOF persistence
   - Configure health check

4. **Create Redis Service** (if Celery) (lines 550-570)
   - Deploy 1 task (always running)

5. **Wait for PostgreSQL Stability** (lines 572-604)
   - Poll service status every 5 seconds
   - Wait until running count == desired count
   - Timeout: 5 minutes

6. **Register Scheduler Task Definition** (lines 319-343)
   - Configure CPU/memory from request
   - Set Airflow environment variables
   - Configure CloudWatch logging

7. **Create Scheduler Service** (lines 459-479)
   - Deploy 1 task (always running)

8. **Register Webserver Task Definition** (lines 345-373)
   - Configure CPU/memory from request
   - Expose port 8080
   - Configure ALB target group (TODO)

9. **Create Webserver Service** (lines 481-501)
   - Deploy 1 task (always running)
   - Configure load balancer integration

10. **Register Worker Task Definition** (if Celery) (lines 375-399)
    - Configure CPU/memory from request
    - Set Celery worker command

11. **Create Worker Service** (if Celery) (lines 503-523)
    - Deploy min workers count
    - Configure auto-scaling

12. **Configure Auto-Scaling** (ECSScalingManager.java:31-57)
    - Register scalable target (min/max workers)
    - Create CPU-based scaling policy (target 70%)
    - Create memory-based scaling policy (target 80%)

**Total Time**: ~5-7 minutes

### 8.4 Upgrade

**API request** (`PUT /api/v1/deployments/{deploymentId}` with full `DeploymentCreateRequest` body, not a partial patch):
```bash
PUT /api/v1/deployments/{deploymentId}
{
  "tenantId": "acme-corp",
  "name": "prod-airflow",
  "description": "Production",
  "airflowVersion": "3.1.8",
  "executorType": "CELERY",
  "schedulerCpu": "2000m",
  "schedulerMemory": "4Gi",
  "webserverCpu": "500m",
  "webserverMemory": "1Gi",
  "workerCpu": "1000m",
  "workerMemory": "2Gi",
  "minWorkers": 1,
  "maxWorkers": 5
}
```

**Actions** (ECSDeploymentProvider.java:103-128):
1. Register new task definitions with updated parameters
2. Update ECS services with new task definitions
3. ECS performs rolling update (zero-downtime)

**Time**: ~3-5 minutes

### 8.5 Scaling

There is **no** `POST .../scale` route. Change worker bounds with **`PUT /api/v1/deployments/{deploymentId}`** by updating `minWorkers` / `maxWorkers` (and resubmitting the full deployment JSON). `ECSScalingManager` re-applies Application Auto Scaling targets when the deployment is updated.

**Time**: ~1-3 minutes depending on service steady state

### 8.6 Uninstall

**API request**:
```bash
DELETE /api/v1/deployments/{deploymentId}
Authorization: Bearer <JWT>
```

**Actions** (ECSDeploymentProvider.java:131-166):
1. Delete Airflow services (scheduler, webserver, workers)
2. Delete database services (postgres, redis)
3. Deregister all task definitions
4. Remove auto-scaling policies
5. ECS drains tasks gracefully

**Time**: ~2-3 minutes

**Note**: EFS data is retained (manual cleanup required if needed)

### 8.7 Tenant Deletion

**API request**:
```bash
DELETE /api/v1/tenants/{tenantId}
Authorization: Bearer <JWT>   # ADMIN
```

**Actions** (ECSCloudProvider.java:65-97):
1. List all services in cluster
2. Force delete all services
3. Delete ECS cluster
4. Delete secrets from AWS Secrets Manager

**Time**: ~2-3 minutes

**Note**: EFS file system is not automatically deleted (manual cleanup)

## 9. Auto-Scaling

### AWS Application Auto Scaling

ECS uses **AWS Application Auto Scaling** for worker auto-scaling (equivalent to KEDA for Kubernetes).

### Scaling Configuration

**Target Tracking Policies** (ECSScalingManager.java:116-168):

```java
// CPU-based scaling
TargetTrackingScalingPolicyConfiguration.builder()
    .targetValue(70.0)  // Target 70% CPU utilization
    .predefinedMetricSpecification(
        PredefinedMetricSpecification.builder()
            .predefinedMetricType(MetricType.ECS_SERVICE_AVERAGE_CPU_UTILIZATION)
            .build()
    )
    .scaleInCooldown(300)   // 5 minutes before scaling in
    .scaleOutCooldown(60)   // 1 minute before scaling out
    .build()

// Memory-based scaling
TargetTrackingScalingPolicyConfiguration.builder()
    .targetValue(80.0)  // Target 80% memory utilization
    .predefinedMetricSpecification(
        PredefinedMetricSpecification.builder()
            .predefinedMetricType(MetricType.ECS_SERVICE_AVERAGE_MEMORY_UTILIZATION)
            .build()
    )
    .scaleInCooldown(300)
    .scaleOutCooldown(60)
    .build()
```

### Scaling Behavior

**Scale-Out Example**:
```
Time 0:00  - 2 workers running, CPU at 50%
Time 0:10  - Task load increases, CPU jumps to 85%
Time 0:20  - Auto-scaling detects high CPU (>70% target)
Time 1:20  - After 60s cooldown, scale-out triggered
Time 1:21  - ECS launches 2 new worker tasks
Time 2:00  - 4 workers running, CPU stabilizes at 60%
```

**Scale-In Example**:
```
Time 0:00  - 4 workers running, CPU at 60%
Time 0:30  - Task load decreases, CPU drops to 40%
Time 1:00  - Auto-scaling detects low CPU (<70% target)
Time 6:00  - After 300s cooldown, scale-in triggered
Time 6:01  - ECS gracefully stops 2 worker tasks
Time 7:00  - 2 workers running, CPU stabilizes at 50%
```

### Scaling Limits

**Configured Limits**:
- Min workers: 1 (from deployment configuration)
- Max workers: 10 (from deployment configuration)
- Scale-out cooldown: 60 seconds
- Scale-in cooldown: 300 seconds (5 minutes)

**Why Different Cooldowns?**
- **Fast scale-out**: Quickly respond to load spikes
- **Slow scale-in**: Avoid thrashing, ensure sustained low load

### Monitoring Scaling

**CloudWatch Metrics**:
```bash
# View CPU utilization
aws cloudwatch get-metric-statistics \
  --namespace AWS/ECS \
  --metric-name CPUUtilization \
  --dimensions Name=ClusterName,Value=managed-airflow-acme-corp \
               Name=ServiceName,Value=prod-airflow-worker

# View memory utilization
aws cloudwatch get-metric-statistics \
  --namespace AWS/ECS \
  --metric-name MemoryUtilization \
  --dimensions Name=ClusterName,Value=managed-airflow-acme-corp \
               Name=ServiceName,Value=prod-airflow-worker
```

**Scaling History**:
```bash
# View scaling activities
aws application-autoscaling describe-scaling-activities \
  --service-namespace ecs \
  --resource-id service/managed-airflow-acme-corp/prod-airflow-worker
```

## 10. Resource Management

### Task Resource Allocation

**Fargate CPU and Memory Combinations**:

| CPU (vCPU) | Memory (GB) | Use Case |
|------------|-------------|----------|
| 0.25 | 0.5, 1, 2 | PostgreSQL (optimized), Redis |
| 0.5 | 1, 2, 3, 4 | Webserver, Scheduler (light) |
| 1 | 2, 3, 4, 5, 6, 7, 8 | Scheduler, Workers |
| 2 | 4-16 | Heavy workers |
| 4 | 8-30 | Very heavy workers |

**Default Configuration** (ECSDeploymentProvider.java):

| Component | CPU | Memory | Count |
|-----------|-----|--------|-------|
| PostgreSQL | 512 (0.5 vCPU) | 1024 MB | 1 |
| Redis | 256 (0.25 vCPU) | 512 MB | 1 |
| Scheduler | 1024 (1 vCPU) | 2048 MB | 1 |
| Webserver | 512 (0.5 vCPU) | 1024 MB | 1 |
| Worker | 1024 (1 vCPU) | 2048 MB | 1-10 |

**Optimized Configuration** (for cost savings):

| Component | CPU | Memory | Count | Monthly Cost |
|-----------|-----|--------|-------|--------------|
| PostgreSQL | 256 (0.25 vCPU) | 512 MB | 1 | ~$7 |
| Redis | 256 (0.25 vCPU) | 256 MB | 1 | ~$4 |
| Scheduler | 512 (0.5 vCPU) | 1024 MB | 1 | ~$14 |
| Webserver | 256 (0.25 vCPU) | 512 MB | 1 | ~$7 |
| Worker | 512 (0.5 vCPU) | 1024 MB | 1-2 | ~$14-28 |
| EFS | - | 10 GB | - | ~$3 |
| **Total** | - | - | - | **~$50/month** |

### Resource Quotas

**AWS Fargate Quotas** (per region):
- Tasks per cluster: 1,000
- Tasks per service: 1,000
- vCPU limit: 1,000 (default, can be increased)

**EFS Quotas**:
- File systems per account: 1,000
- Throughput (bursting mode): Based on size
- Throughput (provisioned mode): Up to 1 GB/s

## 11. Monitoring and Logging

### CloudWatch Logs

**Log Groups** (ECSDeploymentProvider.java:448-457):

```
/ecs/managed-airflow/{deployment-id}/
├── postgres        ← PostgreSQL logs
├── redis           ← Redis logs
├── scheduler       ← Airflow scheduler logs
├── webserver       ← Airflow webserver logs
└── worker          ← Airflow worker logs
```

**Viewing Logs**:
```bash
# Tail scheduler logs
aws logs tail /ecs/managed-airflow/prod-airflow-123/scheduler --follow

# View specific time range
aws logs filter-log-events \
  --log-group-name /ecs/managed-airflow/prod-airflow-123/scheduler \
  --start-time $(date -u -d '1 hour ago' +%s)000

# Search for errors
aws logs filter-log-events \
  --log-group-name /ecs/managed-airflow/prod-airflow-123/scheduler \
  --filter-pattern "ERROR"
```

### CloudWatch Metrics

**ECS Metrics**:
- `CPUUtilization`: Task CPU usage (%)
- `MemoryUtilization`: Task memory usage (%)
- `DesiredTaskCount`: Number of tasks desired
- `RunningTaskCount`: Number of tasks running
- `PendingTaskCount`: Number of tasks starting

**EFS Metrics**:
- `BurstCreditBalance`: Burst credit remaining
- `PercentIOLimit`: Percentage of I/O limit used
- `ThroughputUtilization`: Throughput usage
- `DataReadIOBytes`: Bytes read
- `DataWriteIOBytes`: Bytes written

### Monitoring Dashboard

**CloudWatch Dashboard** (example):
```json
{
  "widgets": [
    {
      "type": "metric",
      "properties": {
        "metrics": [
          ["AWS/ECS", "CPUUtilization", {"stat": "Average"}],
          [".", "MemoryUtilization", {"stat": "Average"}]
        ],
        "period": 300,
        "stat": "Average",
        "region": "us-east-1",
        "title": "ECS Task Utilization"
      }
    },
    {
      "type": "metric",
      "properties": {
        "metrics": [
          ["AWS/EFS", "BurstCreditBalance"],
          [".", "PercentIOLimit"]
        ],
        "period": 300,
        "stat": "Average",
        "region": "us-east-1",
        "title": "EFS Performance"
      }
    }
  ]
}
```

### Health Checks

**Container Health Checks** (defined in task definitions):

**PostgreSQL**:
```json
{
  "healthCheck": {
    "command": ["CMD-SHELL", "pg_isready -U airflow"],
    "interval": 10,
    "timeout": 5,
    "retries": 5,
    "startPeriod": 30
  }
}
```

**Redis**:
```json
{
  "healthCheck": {
    "command": ["CMD", "redis-cli", "ping"],
    "interval": 10,
    "timeout": 5,
    "retries": 5,
    "startPeriod": 30
  }
}
```

**Airflow Components**: Use ALB health checks on `/health` endpoint

### Alerting

**CloudWatch Alarms** (example setup):

```bash
# Alert when CPU usage is high
aws cloudwatch put-metric-alarm \
  --alarm-name "ecs-high-cpu-{deployment-id}" \
  --alarm-description "Alert when CPU exceeds 80%" \
  --metric-name CPUUtilization \
  --namespace AWS/ECS \
  --statistic Average \
  --period 300 \
  --threshold 80 \
  --comparison-operator GreaterThanThreshold \
  --evaluation-periods 2

# Alert when EFS burst credits are low
aws cloudwatch put-metric-alarm \
  --alarm-name "efs-low-burst-credits-{deployment-id}" \
  --alarm-description "Alert when EFS burst credits < 1TB" \
  --metric-name BurstCreditBalance \
  --namespace AWS/EFS \
  --statistic Average \
  --period 300 \
  --threshold 1000000000000 \
  --comparison-operator LessThanThreshold \
  --evaluation-periods 2
```

## 12. Security

### Network Security

**VPC Configuration** (infrastructure/ecs/terraform/main.tf:17-68):
- Private VPC (10.0.0.0/16)
- 2 public subnets (in different AZs)
- Internet Gateway for external access
- Route tables for traffic routing

**Security Groups**:

**ECS Security Group** (main.tf:71-100):
```hcl
ingress {
  from_port   = 8080      # Airflow webserver
  to_port     = 8080
  protocol    = "tcp"
  cidr_blocks = ["0.0.0.0/0"]
}

ingress {
  from_port   = 5555      # Flower (Celery monitoring)
  to_port     = 5555
  protocol    = "tcp"
  cidr_blocks = ["0.0.0.0/0"]
}

egress {
  from_port   = 0         # Allow all outbound
  to_port     = 0
  protocol    = "-1"
  cidr_blocks = ["0.0.0.0/0"]
}
```

**EFS Security Group** (main.tf:102-124):
```hcl
ingress {
  from_port       = 2049  # NFS
  to_port         = 2049
  protocol        = "tcp"
  security_groups = [aws_security_group.ecs.id]  # Only from ECS
}
```

**Best Practices**:
- Restrict webserver access to specific IPs (not 0.0.0.0/0)
- Use AWS WAF for additional protection
- Enable VPC Flow Logs for auditing

### IAM Security

**Task Execution Role** (main.tf:170-213):
- Pulls container images from ECR
- Writes logs to CloudWatch
- Retrieves secrets from Secrets Manager

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken",
        "ecr:BatchCheckLayerAvailability",
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage",
        "logs:CreateLogStream",
        "logs:PutLogEvents",
        "secretsmanager:GetSecretValue",
        "secretsmanager:DescribeSecret"
      ],
      "Resource": "*"
    }
  ]
}
```

**Task Role** (main.tf:215-270):
- Airflow tasks use this role
- Access to S3 (for DAGs, logs)
- Access to CloudWatch Logs

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "s3:*",
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents"
      ],
      "Resource": "arn:aws:logs:us-east-1:*:log-group:/ecs/managed-airflow/*"
    }
  ]
}
```

**Best Practices**:
- Follow least privilege principle
- Limit S3 access to specific buckets
- Use IAM policies for fine-grained control
- Enable CloudTrail for IAM auditing

### Secrets Management

**AWS Secrets Manager** (ECSCloudProvider.java:117-148):

```java
// Store secrets for each tenant
secretsManagerClient.createSecret(
    CreateSecretRequest.builder()
        .name(namespace + "/" + secretName)
        .secretString(secretJson)
        .tags(
            Tag.builder().key("namespace").value(namespace).build(),
            Tag.builder().key("managed-by").value("airflow-control-plane").build()
        )
        .build()
);
```

**Secret Naming**:
- Format: `{cluster-name}/{secret-name}`
- Example: `managed-airflow-acme-corp/db-credentials`

**Best Practices**:
- Rotate secrets regularly
- Use automatic rotation where possible
- Audit secret access via CloudTrail
- Enable encryption at rest

### Encryption

**In Transit**:
- EFS: Transit encryption enabled (TLS)
- PostgreSQL: SSL connections recommended
- Webserver: Use ALB with HTTPS

**At Rest**:
- EFS: Encryption at rest enabled
- Secrets Manager: Encrypted by default
- CloudWatch Logs: Can be encrypted with KMS

## 13. Backup and Recovery

### PostgreSQL Backup

**Option 1: EFS Snapshots via AWS Backup**

```bash
# Create backup vault
aws backup create-backup-vault --backup-vault-name airflow-backups

# Create backup plan
aws backup create-backup-plan --backup-plan '{
  "BackupPlanName": "airflow-daily-backups",
  "Rules": [{
    "RuleName": "daily-backup",
    "TargetBackupVaultName": "airflow-backups",
    "ScheduleExpression": "cron(0 5 * * ? *)",
    "StartWindowMinutes": 60,
    "CompletionWindowMinutes": 120,
    "Lifecycle": {
      "DeleteAfterDays": 30
    }
  }]
}'

# Assign resources
aws backup create-backup-selection --backup-plan-id ... --backup-selection '{
  "SelectionName": "efs-backup",
  "IamRoleArn": "arn:aws:iam::ACCOUNT:role/AWSBackupDefaultServiceRole",
  "Resources": [
    "arn:aws:elasticfilesystem:us-east-1:ACCOUNT:file-system/fs-xxxxxxxxx"
  ]
}'
```

**Option 2: pg_dump via ECS Task**

```bash
# Run one-off backup task
aws ecs run-task \
  --cluster managed-airflow-acme-corp \
  --task-definition postgres-backup \
  --launch-type FARGATE \
  --network-configuration '{
    "awsvpcConfiguration": {
      "subnets": ["subnet-xxx"],
      "securityGroups": ["sg-zzz"],
      "assignPublicIp": "ENABLED"
    }
  }'
```

**Backup Task Definition**:
```json
{
  "family": "postgres-backup",
  "containerDefinitions": [{
    "name": "backup",
    "image": "postgres:13",
    "command": [
      "bash", "-c",
      "pg_dump -h postgres.{deployment-id} -U airflow airflow | aws s3 cp - s3://backups/postgres-$(date +%Y%m%d-%H%M%S).sql"
    ],
    "environment": [
      {"name": "PGPASSWORD", "value": "airflow"}
    ]
  }]
}
```

**Option 3: Automated Scheduled Backups**

Use Amazon EventBridge to trigger backup tasks:

```json
{
  "source": ["aws.events"],
  "detail-type": ["Scheduled Event"],
  "schedule": "cron(0 2 * * ? *)",
  "target": {
    "arn": "arn:aws:ecs:us-east-1:ACCOUNT:cluster/managed-airflow-acme-corp",
    "ecsParameters": {
      "taskDefinitionArn": "arn:aws:ecs:us-east-1:ACCOUNT:task-definition/postgres-backup",
      "launchType": "FARGATE"
    }
  }
}
```

### Recovery

**Restore from EFS Snapshot**:
```bash
# Create new EFS from snapshot
aws efs create-file-system \
  --creation-token restored-airflow-efs \
  --encrypted \
  --restore-source BackupId=SNAPSHOT_ID

# Update task definition to use new EFS
# Redeploy services
```

**Restore from pg_dump**:
```bash
# Copy backup from S3
aws s3 cp s3://backups/postgres-20240101-020000.sql backup.sql

# Run restore task
aws ecs run-task \
  --cluster managed-airflow-acme-corp \
  --task-definition postgres-restore \
  --overrides '{
    "containerOverrides": [{
      "name": "restore",
      "command": ["bash", "-c", "aws s3 cp s3://backups/postgres-20240101-020000.sql - | psql -h postgres.{deployment-id} -U airflow airflow"]
    }]
  }'
```

### Disaster Recovery

**RTO (Recovery Time Objective)**: ~30 minutes
**RPO (Recovery Point Objective)**: Depends on backup frequency

**DR Steps**:
1. Restore EFS from latest snapshot (~15 minutes)
2. Update EFS configuration in application.yml
3. Redeploy Airflow services (~10 minutes)
4. Verify data integrity (~5 minutes)

## 14. Cost Analysis

### Monthly Cost Breakdown (Per Tenant)

**Default Configuration**:

| Resource | Specification | Hours/Month | Unit Cost | Monthly Cost |
|----------|---------------|-------------|-----------|--------------|
| **Fargate - PostgreSQL** | 0.5 vCPU, 1 GB | 730 | $0.04048/hr | $29.55 |
| **Fargate - Redis** | 0.25 vCPU, 0.5 GB | 730 | $0.02024/hr | $14.78 |
| **Fargate - Scheduler** | 1 vCPU, 2 GB | 730 | $0.08096/hr | $59.10 |
| **Fargate - Webserver** | 0.5 vCPU, 1 GB | 730 | $0.04048/hr | $29.55 |
| **Fargate - Workers** | 1 vCPU, 2 GB × 2 | 1460 | $0.08096/hr | $118.20 |
| **EFS Storage** | 20 GB | - | $0.30/GB-month | $6.00 |
| **Data Transfer** | Inter-AZ | - | $0.01/GB | $1.00 |
| **CloudWatch Logs** | 10 GB | - | $0.50/GB | $5.00 |
| **ALB** | 1 ALB | 730 | $0.0225/hr | $16.43 |
| **Total** | - | - | - | **$279.61** |

**Optimized Configuration** (Cost-Effective):

| Resource | Specification | Hours/Month | Unit Cost | Monthly Cost |
|----------|---------------|-------------|-----------|--------------|
| **Fargate - PostgreSQL** | 0.25 vCPU, 0.5 GB | 730 | $0.02024/hr | $14.78 |
| **Fargate - Redis** | 0.25 vCPU, 0.25 GB | 730 | $0.01518/hr | $11.08 |
| **Fargate - Scheduler** | 0.5 vCPU, 1 GB | 730 | $0.04048/hr | $29.55 |
| **Fargate - Webserver** | 0.25 vCPU, 0.5 GB | 730 | $0.02024/hr | $14.78 |
| **Fargate - Workers** | 0.5 vCPU, 1 GB × 1-2 avg | 1095 | $0.04048/hr | $44.33 |
| **EFS Storage** | 10 GB | - | $0.30/GB-month | $3.00 |
| **Data Transfer** | Inter-AZ | - | $0.01/GB | $0.50 |
| **CloudWatch Logs** | 5 GB | - | $0.50/GB | $2.50 |
| **ALB** | 1 ALB | 730 | $0.0225/hr | $16.43 |
| **Total** | - | - | - | **$136.95** |

**Ultra-Optimized Configuration** (Test/Dev):

| Resource | Specification | Hours/Month | Unit Cost | Monthly Cost |
|----------|---------------|-------------|-----------|--------------|
| **Fargate - PostgreSQL** | 0.25 vCPU, 0.5 GB | 730 | $0.02024/hr | $14.78 |
| **Fargate - Scheduler** | 0.5 vCPU, 1 GB | 730 | $0.04048/hr | $29.55 |
| **Fargate - Webserver** | 0.25 vCPU, 0.5 GB | 730 | $0.02024/hr | $14.78 |
| **EFS Storage** | 5 GB | - | $0.30/GB-month | $1.50 |
| **CloudWatch Logs** | 2 GB | - | $0.50/GB | $1.00 |
| **ALB** | 1 ALB (shared) | 730 | $0.0225/hr | $0 (shared) |
| **Total** | - | - | - | **$61.61** |

*Using LocalExecutor (no Redis or workers needed)*

### Cost Optimization Strategies

1. **Use Fargate Spot** (up to 70% savings):
   ```java
   .capacityProviderStrategy(
       CapacityProviderStrategyItem.builder()
           .capacityProvider("FARGATE_SPOT")
           .weight(1)
           .build()
   )
   ```

2. **Optimize EFS Storage**:
   - Enable lifecycle policies (transition to IA after 30 days)
   - Clean up old PostgreSQL data
   - Use EFS Infrequent Access (IA) for backups

3. **Reduce CloudWatch Logs Retention**:
   - Default: 7 days (infrastructure/ecs/terraform/main.tf:275)
   - Consider 3 days for test environments

4. **Share ALB Across Tenants**:
   - Use host-based routing
   - Saves ~$16/month per tenant

5. **Use Reserved Capacity** (for production):
   - AWS Savings Plans for Fargate
   - Up to 52% savings with 1-year commitment

### Cost Comparison

| Deployment Option | Monthly Cost (per tenant) | Best For |
|-------------------|---------------------------|----------|
| **ECS Optimized** | $136.95 | Small production, staging |
| **ECS Ultra-Optimized** | $61.61 | Test, dev, PoC |
| **EC2 Docker** | $35.00 | Dev, test (single instance) |
| **ECS with RDS/ElastiCache** | $200+ | Production (managed DBs) |
| **Kubernetes** | $150+ | Multi-tenant production |

## 15. Scalability

### Vertical Scaling (Task Resources)

**Per-Task Limits**:
- Max CPU: 4 vCPU per task
- Max Memory: 30 GB per task

**Scaling Up**:
```java
// Update task definition with higher resources
String schedulerTaskDef = registerSchedulerTaskDefinition(deployment);
// CPU: 1024 → 2048
// Memory: 2048 → 4096

// Update service
updateService(clusterName, serviceName, schedulerTaskDef);
```

### Horizontal Scaling (Task Count)

**Worker Scaling**:
- Min: 1 worker
- Max: 1,000 workers (per service limit)
- Auto-scales based on CPU/memory

**Manual Scaling**:
```bash
aws ecs update-service \
  --cluster managed-airflow-acme-corp \
  --service prod-airflow-worker \
  --desired-count 5
```

### Cluster Scaling

**Per-Cluster Limits**:
- Tasks per cluster: 1,000
- Services per cluster: 1,000
- Total vCPU: 1,000 (default quota, can be increased)

**Multi-Tenant Scaling**:
```
1 tenant  = 1 cluster  = ~6 tasks  = ~4 vCPU
10 tenants = 10 clusters = ~60 tasks = ~40 vCPU
100 tenants = 100 clusters = ~600 tasks = ~400 vCPU
```

### Database Scaling Limits

**PostgreSQL on EFS**:
- **Connections**: Max ~100 concurrent connections
- **Throughput**: Bursting mode (50 MB/s per TB of data)
- **IOPS**: ~7,000 IOPS (baseline)
- **Best For**: <1,000 tasks/day

**When to Upgrade to RDS**:
- >1,000 tasks/day
- >100 concurrent connections
- Need read replicas
- Require automatic failover

### Scalability Recommendations

| Workload Size | Database | Worker Count | Configuration |
|---------------|----------|--------------|---------------|
| **Small** (<100 tasks/day) | PostgreSQL on EFS | 1-2 | Optimized config |
| **Medium** (100-1000 tasks/day) | PostgreSQL on EFS | 2-5 | Default config |
| **Large** (1000-5000 tasks/day) | RDS PostgreSQL | 5-20 | High-performance |
| **Very Large** (>5000 tasks/day) | RDS PostgreSQL Multi-AZ | 20-100 | Enterprise config |

## 16. Comparison with Other Options

### Feature Comparison

| Feature | ECS (Docker) | EC2 (Docker) | ECS (RDS/ElastiCache) | Kubernetes |
|---------|--------------|--------------|------------------------|------------|
| **Cost** | ~$137/mo | ~$35/mo | ~$200/mo | ~$150/mo |
| **Setup Complexity** | Easy | Easiest | Easy | Complex |
| **Maintenance** | Low | Medium | Low | High |
| **Auto-Scaling** | Yes (Application Auto Scaling) | Manual | Yes | Yes (KEDA) |
| **High Availability** | Medium (multi-AZ) | Low (single instance) | High (multi-AZ) | High (multi-node) |
| **Database** | Containerized PostgreSQL | Containerized PostgreSQL | AWS RDS | Containerized/External |
| **Message Broker** | Containerized Redis | Containerized Redis | AWS ElastiCache | Containerized/External |
| **Persistent Storage** | AWS EFS | EBS volumes | RDS/ElastiCache | Persistent Volumes |
| **Backup Strategy** | EFS snapshots, pg_dump | EBS snapshots, pg_dump | Automated RDS backups | Manual/Velero |
| **Monitoring** | CloudWatch | CloudWatch + manual | CloudWatch | Prometheus/Grafana |
| **Networking** | AWS VPC, ALB | AWS VPC, ALB/nginx | AWS VPC, ALB | K8s networking, Ingress |
| **Multi-Tenancy** | Cluster per tenant | Instance per tenant | Cluster per tenant | Namespace per tenant |
| **Max Throughput** | Medium (~1000 tasks/day) | Low (~500 tasks/day) | High (>5000 tasks/day) | Very High |
| **Best For** | Test, staging, small prod | Dev, test, PoC | Production | Enterprise, multi-tenant |

### When to Choose ECS (Docker-Based)

**Choose ECS with Containerized PostgreSQL/Redis When**:
- You need auto-scaling and self-healing
- Cost is a primary concern
- Workload is <1,000 tasks/day
- You want serverless (no EC2 management)
- You prefer AWS-native solutions
- You need faster deployment than Kubernetes
- You're building a multi-tenant platform

**Don't Choose ECS (Docker-Based) When**:
- You need maximum database performance (use RDS)
- You require high availability SLAs
- Workload exceeds 5,000 tasks/day
- You need read replicas
- Compliance requires managed database services
- You need point-in-time recovery
- Budget allows for managed services

### Migration Path

**From ECS (Docker) to ECS (Managed Services)**:
1. Export PostgreSQL data: `pg_dump`
2. Provision RDS PostgreSQL and ElastiCache Redis
3. Import data to RDS
4. Update task definitions to use RDS/ElastiCache endpoints
5. Redeploy services
6. Decommission containerized PostgreSQL/Redis

**From ECS to Kubernetes**:
1. Convert task definitions to Kubernetes Deployments
2. Create Helm chart
3. Deploy to Kubernetes cluster
4. Migrate data
5. Update DNS

## 17. Best Practices

### Design Best Practices

1. **Use Separate Clusters Per Tenant**
   - Strong isolation between tenants
   - Independent scaling and resource management
   - Failure isolation

2. **Implement Health Checks**
   - Configure container health checks (see section 11)
   - Use ALB health checks for webserver
   - Set appropriate timeout and retry values

3. **Enable Auto-Scaling**
   - Always configure auto-scaling for workers
   - Use target tracking policies
   - Set appropriate min/max limits

4. **Use EFS Lifecycle Policies**
   - Transition old data to Infrequent Access (IA)
   - Reduce storage costs by up to 92%

5. **Implement Proper Logging**
   - Use CloudWatch Logs for all components
   - Set appropriate retention periods
   - Use log filters for alerting

### Security Best Practices

1. **Network Segmentation**
   - Use private subnets for databases
   - Restrict security group rules
   - Enable VPC Flow Logs

2. **Secrets Management**
   - Never hardcode credentials
   - Use AWS Secrets Manager
   - Enable automatic rotation

3. **IAM Least Privilege**
   - Separate task execution role and task role
   - Grant only necessary permissions
   - Use resource-based policies

4. **Encryption**
   - Enable EFS encryption at rest
   - Use TLS for EFS transit encryption
   - Use HTTPS for webserver (ALB with ACM)

### Operational Best Practices

1. **Backup Strategy**
   - Daily EFS snapshots via AWS Backup
   - Weekly pg_dump to S3
   - Test restore procedures regularly

2. **Monitoring and Alerting**
   - Set up CloudWatch alarms for CPU/memory
   - Monitor EFS burst credits
   - Alert on service failures

3. **Cost Management**
   - Use Fargate Spot for non-critical workloads
   - Enable EFS lifecycle policies
   - Share ALB across tenants where possible
   - Monitor costs with AWS Cost Explorer

4. **Capacity Planning**
   - Monitor task resource utilization
   - Right-size task resources
   - Plan for growth with AWS quotas

5. **Deployment Strategy**
   - Use rolling updates for zero-downtime
   - Test in staging environment first
   - Have rollback plan ready

## 18. Troubleshooting

### Common Issues

#### Issue 1: PostgreSQL Container Keeps Restarting

**Symptoms**:
```
Service: prod-airflow-postgres
Running count: 0
Desired count: 1
Status: Tasks failing health checks
```

**Diagnosis**:
```bash
# Check logs
aws logs tail /ecs/managed-airflow/prod-airflow/postgres --follow

# Common errors:
# - "permission denied" → EFS mount permissions
# - "cannot allocate memory" → Insufficient task memory
# - "port 5432 already in use" → Multiple tasks on same host (shouldn't happen with Fargate)
```

**Solutions**:
```bash
# Fix EFS permissions
aws efs put-lifecycle-configuration \
  --file-system-id fs-xxxxxxxxx \
  --lifecycle-policies ...

# Increase task memory
# Update task definition: memory: 1024 → 2048
terraform apply

# Check security groups
aws ec2 describe-security-groups --group-ids sg-xxxxxxxxx
```

#### Issue 2: Services Cannot Connect to PostgreSQL

**Symptoms**:
```
Scheduler logs: "connection refused" or "timeout connecting to database"
```

**Diagnosis**:
```bash
# Verify PostgreSQL is running
aws ecs describe-services \
  --cluster managed-airflow-acme-corp \
  --services prod-airflow-postgres

# Check security group rules
aws ec2 describe-security-groups --group-ids sg-xxxxxxxxx

# Verify service discovery
aws servicediscovery list-services
```

**Solutions**:
```bash
# Update security group (allow port 5432)
aws ec2 authorize-security-group-ingress \
  --group-id sg-xxxxxxxxx \
  --protocol tcp \
  --port 5432 \
  --source-group sg-yyyyyyyyy

# Configure service discovery (ECSDeploymentProvider.java:541-543)
# Currently has TODO - needs implementation

# Verify DNS resolution
aws ecs execute-command \
  --cluster managed-airflow-acme-corp \
  --task TASK_ID \
  --container scheduler \
  --command "nslookup postgres.prod-airflow"
```

#### Issue 3: EFS Performance Issues (Slow Queries)

**Symptoms**:
```
PostgreSQL logs: Slow query warnings
CloudWatch: High PercentIOLimit metric
```

**Diagnosis**:
```bash
# Check EFS metrics
aws cloudwatch get-metric-statistics \
  --namespace AWS/EFS \
  --metric-name PercentIOLimit \
  --dimensions Name=FileSystemId,Value=fs-xxxxxxxxx \
  --statistics Average \
  --start-time 2024-01-01T00:00:00Z \
  --end-time 2024-01-01T23:59:59Z \
  --period 3600

# Check burst credits
aws cloudwatch get-metric-statistics \
  --namespace AWS/EFS \
  --metric-name BurstCreditBalance \
  --dimensions Name=FileSystemId,Value=fs-xxxxxxxxx
```

**Solutions**:
```bash
# Option 1: Upgrade to Provisioned Throughput mode
aws efs put-file-system-throughput-mode \
  --file-system-id fs-xxxxxxxxx \
  --throughput-mode provisioned \
  --provisioned-throughput-in-mibps 100

# Option 2: Increase EFS size (to get more burst credits)
# Add more data to EFS or create dummy files

# Option 3: Migrate to RDS PostgreSQL
# See section 16 for migration path
```

#### Issue 4: Auto-Scaling Not Working

**Symptoms**:
```
Workers not scaling up despite high CPU
Scaling policies exist but not triggering
```

**Diagnosis**:
```bash
# Check scaling policies
aws application-autoscaling describe-scaling-policies \
  --service-namespace ecs \
  --resource-id service/managed-airflow-acme-corp/prod-airflow-worker

# View scaling activities
aws application-autoscaling describe-scaling-activities \
  --service-namespace ecs \
  --resource-id service/managed-airflow-acme-corp/prod-airflow-worker

# Check CloudWatch alarms
aws cloudwatch describe-alarms \
  --alarm-name-prefix prod-airflow-worker
```

**Solutions**:
```bash
# Verify scalable target is registered
aws application-autoscaling describe-scalable-targets \
  --service-namespace ecs \
  --resource-ids service/managed-airflow-acme-corp/prod-airflow-worker

# Re-register if missing (ECSScalingManager.java:103-114)
curl -s -X PUT http://localhost:8080/api/v1/deployments/{deploymentId} \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"tenantId":"acme-corp","name":"prod-airflow","airflowVersion":"3.1.8","executorType":"CELERY","minWorkers":1,"maxWorkers":10,"schedulerCpu":"1000m","schedulerMemory":"2Gi","webserverCpu":"500m","webserverMemory":"1Gi","workerCpu":"1000m","workerMemory":"2Gi"}'

# Check IAM permissions
# Ensure task role has autoscaling permissions
```

#### Issue 5: Task Failed to Start

**Symptoms**:
```
ECS Error: "CannotPullContainerError"
ECS Error: "ResourceInitializationError"
```

**Diagnosis**:
```bash
# Check stopped tasks
aws ecs describe-tasks \
  --cluster managed-airflow-acme-corp \
  --tasks $(aws ecs list-tasks --cluster managed-airflow-acme-corp --service-name prod-airflow-scheduler --desired-status STOPPED | jq -r '.taskArns[0]')

# Common errors:
# - CannotPullContainerError → ECR permissions
# - ResourceInitializationError → EFS mount failed
# - OutOfMemoryError → Insufficient task memory
```

**Solutions**:
```bash
# Fix ECR permissions (add to task execution role)
{
  "Effect": "Allow",
  "Action": [
    "ecr:GetAuthorizationToken",
    "ecr:BatchCheckLayerAvailability",
    "ecr:GetDownloadUrlForLayer",
    "ecr:BatchGetImage"
  ],
  "Resource": "*"
}

# Fix EFS mount (check security group)
aws ec2 authorize-security-group-ingress \
  --group-id <efs-sg> \
  --protocol tcp \
  --port 2049 \
  --source-group <ecs-sg>

# Increase task resources
# Update task definition in ECSDeploymentProvider
```

### Debug Commands

```bash
# List all ECS clusters
aws ecs list-clusters

# Describe cluster
aws ecs describe-clusters --clusters managed-airflow-acme-corp

# List services in cluster
aws ecs list-services --cluster managed-airflow-acme-corp

# Describe service
aws ecs describe-services \
  --cluster managed-airflow-acme-corp \
  --services prod-airflow-scheduler

# List tasks
aws ecs list-tasks --cluster managed-airflow-acme-corp

# Describe task
aws ecs describe-tasks \
  --cluster managed-airflow-acme-corp \
  --tasks <task-id>

# View logs
aws logs tail /ecs/managed-airflow/prod-airflow/scheduler --follow

# Execute command in container (requires enabling ECS Exec)
aws ecs execute-command \
  --cluster managed-airflow-acme-corp \
  --task <task-id> \
  --container scheduler \
  --command "/bin/bash" \
  --interactive
```

## 19. Conclusion

The ECS containerized deployment option provides an excellent balance of simplicity, scalability, and cost-effectiveness for Apache Airflow deployments. By containerizing PostgreSQL and Redis instead of using managed AWS services, you can:

- **Reduce costs** by ~30-40% compared to RDS/ElastiCache
- **Simplify infrastructure** with fewer AWS services to manage
- **Maintain consistency** across deployment options (all Docker-based)
- **Enable faster deployments** without waiting for RDS provisioning
- **Leverage ECS benefits** like auto-scaling, self-healing, and serverless compute

This approach is ideal for:
- Test and staging environments
- Small to medium production workloads (<1,000 tasks/day)
- Cost-conscious multi-tenant platforms
- Teams wanting ECS orchestration without managed service complexity

For larger production workloads or when maximum database performance and high availability are required, consider upgrading to ECS with RDS PostgreSQL and ElastiCache Redis.

---

**Next Steps**:
1. Review the [ECS Terraform configuration](../infrastructure/ecs/terraform/)
2. Read the [ECS Docker Update Guide](../infrastructure/ecs/ECS_DOCKER_UPDATE.md)
3. Compare with [EC2 architecture](./ARCHITECTURE_EC2.md) and [Kubernetes architecture](./ARCHITECTURE.md)
4. Deploy your first ECS-based Airflow instance
5. Monitor costs and performance
6. Plan scaling strategy based on growth

**Related Documentation**:
- [Main Architecture (Kubernetes)](./ARCHITECTURE.md)
- [EC2 Deployment Architecture](./ARCHITECTURE_EC2.md)
- [ECS Docker Update Guide](../infrastructure/ecs/ECS_DOCKER_UPDATE.md)
- [Deployment Options Comparison](../README.md#deployment-options)

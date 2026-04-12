# EC2 with Docker Implementation for Managed Airflow Platform

## Overview

The Managed Airflow Platform now supports **AWS EC2 with Docker Compose** as the simplest and most cost-effective deployment option for test and development environments. This implementation provides a straightforward alternative to Kubernetes and ECS, using battle-tested Docker Compose for Airflow deployment.

## Architecture

The implementation extends the provider abstraction pattern to support EC2 instances with Docker:

```
┌─────────────────────────────────┐
│   AirflowDeploymentService      │
│   (Cloud-agnostic orchestration)│
└─────────────┬───────────────────┘
              │
              ├─── DeploymentProvider (interface)
              │         │
              │         ├─── HelmDeploymentProvider (Kubernetes)
              │         ├─── ECSDeploymentProvider (AWS ECS)
              │         └─── EC2DeploymentProvider (AWS EC2 + Docker)
              │
              └─── CloudProvider (interface)
                        │
                        ├─── KubernetesCloudProvider
                        ├─── ECSCloudProvider
                        └─── EC2CloudProvider
```

## Key Components

### 1. Core Services

**EC2CommandExecutor** (`service/EC2CommandExecutor.java`)
- Executes commands on EC2 instances via AWS Systems Manager (SSM)
- No SSH required - uses SSM Session Manager
- Supports synchronous and asynchronous command execution
- Handles command status tracking and result retrieval

**DockerComposeGenerator** (`service/DockerComposeGenerator.java`)
- Generates docker-compose.yml files for Airflow deployments
- Supports all executor types (Local, Celery, etc.)
- Configures resource limits and health checks
- Creates complete Airflow environment with PostgreSQL and Redis

### 2. Provider Implementations

**EC2CloudProvider** (`provider/impl/EC2CloudProvider.java`)
- Manages EC2 instances as "namespaces" (one per tenant)
- Automatically installs Docker and Docker Compose
- Configures SSM agent for remote management
- Handles instance lifecycle (create, delete, status)

**EC2DeploymentProvider** (`provider/impl/EC2DeploymentProvider.java`)
- Deploys Airflow using Docker Compose
- Manages deployment lifecycle (deploy, upgrade, uninstall, scale)
- Copies configuration files to instances
- Executes docker-compose commands remotely

### 3. Configuration

**EC2AWSConfig** (in `config/AWSConfig.java`)
- Configures AWS SDK clients (conditional on `deployment.provider=ec2`)
- Creates beans for:
  - Ec2Client
  - SsmClient

**application.yml** (updated)
- New `ec2` profile for EC2 deployments
- Configuration properties for:
  - AWS region
  - AMI ID (Amazon Linux 2)
  - Instance type and key pair
  - VPC/networking settings
  - SSM command timeout

### 4. Infrastructure as Code

**CloudFormation Template** (`infrastructure/ec2/cloudformation-ec2-infrastructure.yaml`)
- Simplified infrastructure setup:
  - VPC with public subnet
  - Security groups (ports 22, 8080, 5555)
  - IAM roles for EC2 instances and control plane
  - SSM permissions

**Terraform Templates** (`infrastructure/ec2/terraform/`)
- `main.tf` - Infrastructure resources
- `variables.tf` - Configurable parameters
- `outputs.tf` - Outputs for application configuration
- Automatically finds latest Amazon Linux 2 AMI

## How It Works

### Deployment Flow

1. **Tenant Creation**
   - EC2CloudProvider launches a new EC2 instance
   - Instance automatically installs SSM agent
   - Docker and Docker Compose are installed
   - Directories created: `/opt/airflow/deployments/`

2. **Airflow Deployment**
   - DockerComposeGenerator creates docker-compose.yml
   - Files copied to EC2 instance via SSM
   - docker-compose up -d executed remotely
   - Services start: PostgreSQL, Redis, Scheduler, Webserver, Workers

3. **Scaling**
   - docker-compose scale command executed
   - Workers scaled up or down as needed

4. **Updates**
   - New docker-compose.yml generated with updated config
   - docker-compose up -d --force-recreate executed
   - Rolling restart of services

5. **Deletion**
   - docker-compose down -v executed
   - Deployment directory removed
   - On tenant deletion: EC2 instance terminated

### Docker Compose Structure

Each deployment creates this structure on the EC2 instance:

```
/opt/airflow/deployments/<deployment-id>/
├── docker-compose.yml    # Generated Airflow configuration
├── .env                  # Environment variables
├── dags/                 # DAG files
├── logs/                 # Airflow logs
└── plugins/              # Airflow plugins
```

Example docker-compose.yml includes:
- PostgreSQL (metadata database)
- Redis (Celery broker, if needed)
- Airflow Scheduler
- Airflow Webserver (port 8080)
- Airflow Worker(s) (if CeleryExecutor)
- Flower (Celery monitoring, port 5555)

## Feature Comparison

| Feature | EC2 + Docker | ECS | Kubernetes |
|---------|-------------|-----|------------|
| Multi-tenancy | EC2 instances | Clusters | Namespaces |
| Deployment method | Docker Compose | Task definitions | Helm charts |
| Remote execution | AWS SSM | Native ECS API | kubectl |
| Auto-scaling | Manual | Application Auto Scaling | KEDA |
| Database | Local PostgreSQL | RDS | Various |
| Message broker | Local Redis | ElastiCache | Various |
| Setup complexity | Lowest | Medium | Highest |
| Monthly cost (est.) | $35-40 | $70-100 | $200-300 |
| Best for | Dev/Test | Test/Staging | Production |
| Activation | `deployment.provider=ec2` | `deployment.provider=ecs` | `deployment.provider=kubernetes` |

## Benefits

1. **Simplicity**: Docker Compose is familiar and easy to understand
2. **Cost-effective**: Lowest infrastructure cost (~$35/month per tenant)
3. **Fast setup**: Deploy Airflow in minutes
4. **No orchestration overhead**: No Kubernetes or ECS complexity
5. **Isolation**: Each tenant gets a dedicated EC2 instance
6. **Flexibility**: Easy to customize docker-compose.yml
7. **SSH-free**: All management via AWS Systems Manager

## Trade-offs

1. **No high availability**: Single instance per tenant
2. **Manual scaling**: No auto-scaling like ECS/Kubernetes
3. **Limited resilience**: Instance failure requires manual intervention
4. **Resource efficiency**: Less efficient than container orchestration
5. **Local storage**: Data on instance (should backup to S3)

## Files Created/Modified

### New Files

**Core Services:**
- `control-plane/src/main/java/com/airflow/platform/service/EC2CommandExecutor.java`
- `control-plane/src/main/java/com/airflow/platform/service/DockerComposeGenerator.java`

**EC2 Providers:**
- `control-plane/src/main/java/com/airflow/platform/provider/impl/EC2CloudProvider.java`
- `control-plane/src/main/java/com/airflow/platform/provider/impl/EC2DeploymentProvider.java`

**Infrastructure:**
- `infrastructure/ec2/README.md`
- `infrastructure/ec2/cloudformation-ec2-infrastructure.yaml`
- `infrastructure/ec2/terraform/main.tf`
- `infrastructure/ec2/terraform/variables.tf`
- `infrastructure/ec2/terraform/outputs.tf`

### Modified Files

- `control-plane/src/main/java/com/airflow/platform/config/AWSConfig.java`
  - Added EC2AWSConfig class for EC2 and SSM clients

- `control-plane/src/main/resources/application.yml`
  - Added `ec2` profile configuration

- `control-plane/pom.xml`
  - Added EC2 and SSM SDK dependencies

## Usage Examples

### Running with EC2

```bash
# Build the control plane
mvn clean package

# Run with EC2 profile
java -jar target/managed-airflow-control-plane-0.0.1-SNAPSHOT.jar --spring.profiles.active=ec2

# Or with Maven
mvn spring-boot:run -Dspring-boot.run.profiles=ec2
```

### Creating a Deployment

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | jq -r '.accessToken')

# Create tenant (launches EC2 instance). tenantId is returned (slug from name).
TENANT_JSON=$(curl -s -X POST http://localhost:8080/api/v1/tenants \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "name": "Test Tenant",
    "email": "test@example.com",
    "organization": "Test Org",
    "cloudProvider": "AWS",
    "clusterName": "ec2",
    "region": "us-east-1"
  }')
TENANT_ID=$(echo "$TENANT_JSON" | jq -r '.tenantId')

# Deploy Airflow (deploymentId is generated from name)
curl -s -X POST http://localhost:8080/api/v1/deployments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "{
    \"tenantId\": \"$TENANT_ID\",
    \"name\": \"dev-airflow\",
    \"description\": \"EC2 dev\",
    \"airflowVersion\": \"3.1.8\",
    \"executorType\": \"CELERY\",
    \"minWorkers\": 2,
    \"maxWorkers\": 5
  }" | jq .
```

### Accessing Airflow

After deployment, access Airflow at:
- **Webserver**: `http://<instance-ip>:8080`
- **Credentials**: admin / admin (default)
- **Flower**: `http://<instance-ip>:5555` (if CeleryExecutor)

## AWS Systems Manager (SSM) Integration

### Why SSM?

- **No SSH required**: Eliminates need for SSH keys and bastion hosts
- **Secure**: All traffic encrypted, no open SSH ports needed
- **Auditable**: All commands logged to CloudTrail
- **Private subnets**: Works in private subnets without NAT
- **IAM integration**: Uses IAM roles for authentication

### Command Execution Flow

```
Control Plane
     ↓ (via AWS SDK)
SSM Service
     ↓ (secure channel)
SSM Agent on EC2
     ↓ (local execution)
Docker Compose
```

### Example Commands

```java
// Execute command
commandExecutor.executeCommand(instanceId,
    List.of("cd /opt/airflow/deployments/test",
            "sudo docker-compose ps"));

// Copy file
commandExecutor.copyFileToInstance(instanceId,
    dockerComposeContent,
    "/opt/airflow/deployments/test/docker-compose.yml");
```

## Resource Configuration

### CPU and Memory

Docker Compose resource limits are configured based on deployment settings:

```yaml
deploy:
  resources:
    limits:
      cpus: '1.0'      # Converted from millicores (1000)
      memory: 2048M    # From deployment config
```

### Worker Scaling

```yaml
deploy:
  replicas: 2        # From minWorkers setting
```

Scaled dynamically with:
```bash
docker-compose up -d --scale airflow-worker=5
```

## Monitoring and Debugging

### View Logs

```bash
# Via SSM Session Manager
aws ssm start-session --target <instance-id>

# Check Docker Compose logs
cd /opt/airflow/deployments/<deployment-id>
sudo docker-compose logs -f airflow-scheduler
```

### Check Status

```bash
# Container status
sudo docker-compose ps

# Resource usage
sudo docker stats

# Disk usage
df -h
du -sh /opt/airflow/deployments/*
```

## Best Practices

1. **Instance sizing**: Start with t3.medium, monitor and adjust
2. **Backups**: Copy DAGs and logs to S3 regularly
3. **Updates**: Keep Docker images updated
4. **Monitoring**: Enable CloudWatch agent for metrics
5. **Security**: Restrict security group access to known IPs
6. **Networking**: Use VPC endpoints to reduce costs

## Future Enhancements

1. **Automated backups**: S3 sync for DAGs and logs
2. **Health checks**: Automated container health monitoring
3. **Log aggregation**: CloudWatch Logs integration
4. **Metrics**: Push Docker metrics to CloudWatch
5. **Auto-recovery**: Automatically restart failed containers
6. **Blue-green deploys**: Zero-downtime updates
7. **Shared instances**: Multiple deployments per instance for lower cost

## Known Limitations

1. **Single point of failure**: No high availability
2. **Manual intervention**: Requires manual recovery from failures
3. **Instance limits**: Limited by single instance capacity
4. **Persistent storage**: Data on instance EBS volume
5. **Networking**: Single IP address per tenant

## Conclusion

The EC2 with Docker implementation provides the simplest and most cost-effective way to deploy Apache Airflow for test and development environments. It's perfect for:

- **Development teams** testing DAGs
- **POC and evaluation** of Airflow
- **Small-scale production** (non-critical workloads)
- **Cost-conscious deployments** ($35/month vs $200+ for Kubernetes)
- **Learning and training** environments

Combined with ECS and Kubernetes options, the Managed Airflow Platform now supports deployment targets across the entire spectrum from simple dev environments to large-scale production deployments.

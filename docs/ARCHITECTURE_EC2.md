# EC2 Docker Deployment Architecture

## Overview

The EC2 deployment option provides the simplest architecture for running Apache Airflow using Docker Compose on AWS EC2 instances. This approach is ideal for development, testing, and small-scale production workloads.

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         Users / Admins                          │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Control Plane API                            │
│              (Spring Boot REST API)                             │
│  ┌──────────────┬──────────────┬───────────────────────────┐  │
│  │   Tenant     │  Deployment  │    EC2                    │  │
│  │  Management  │  Management  │    Management             │  │
│  └──────────────┴──────────────┴───────────────────────────┘  │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼ AWS SDK (EC2, SSM)
┌─────────────────────────────────────────────────────────────────┐
│                         AWS Cloud                               │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │        EC2 Instance (Tenant 1) - t3.medium               │  │
│  │  ┌────────────────────────────────────────────────────┐  │  │
│  │  │          Docker Compose Stack                      │  │  │
│  │  │  ┌──────────────────────────────────────────────┐  │  │  │
│  │  │  │  postgres:13  (Port 5432)                    │  │  │  │
│  │  │  │  - Volume: postgres-db-volume                │  │  │  │
│  │  │  └──────────────────────────────────────────────┘  │  │  │
│  │  │  ┌──────────────────────────────────────────────┐  │  │  │
│  │  │  │  redis:7-alpine (Port 6379)                  │  │  │  │
│  │  │  └──────────────────────────────────────────────┘  │  │  │
│  │  │  ┌──────────────────────────────────────────────┐  │  │  │
│  │  │  │  airflow-scheduler                           │  │  │  │
│  │  │  │  - Image: apache/airflow:3.1.8               │  │  │  │
│  │  │  │  - CPU: 1.0, Memory: 2GB                     │  │  │  │
│  │  │  └──────────────────────────────────────────────┘  │  │  │
│  │  │  ┌──────────────────────────────────────────────┐  │  │  │
│  │  │  │  airflow-webserver (Port 8080)               │  │  │  │
│  │  │  │  - Image: apache/airflow:3.1.8               │  │  │  │
│  │  │  │  - CPU: 0.5, Memory: 1GB                     │  │  │  │
│  │  │  └──────────────────────────────────────────────┘  │  │  │
│  │  │  ┌──────────────────────────────────────────────┐  │  │  │
│  │  │  │  airflow-worker (x2 replicas)                │  │  │  │
│  │  │  │  - Image: apache/airflow:3.1.8               │  │  │  │
│  │  │  │  - CPU: 1.0, Memory: 2GB                     │  │  │  │
│  │  │  └──────────────────────────────────────────────┘  │  │  │
│  │  │  ┌──────────────────────────────────────────────┐  │  │  │
│  │  │  │  airflow-flower (Port 5555)                  │  │  │  │
│  │  │  │  - Celery monitoring UI                      │  │  │  │
│  │  │  └──────────────────────────────────────────────┘  │  │  │
│  │  └────────────────────────────────────────────────────┘  │  │
│  │     Deployment Dir: /opt/airflow/deployments/ID          │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │        EC2 Instance (Tenant 2) - t3.medium               │  │
│  │  └─── Similar Docker Compose Stack                       │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │        AWS Systems Manager (SSM)                         │  │
│  │  - Command execution without SSH                         │  │
│  │  - Session management                                    │  │
│  │  - Secure, audited access                                │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## Core Components

### 1. Control Plane Components

#### 1.1 EC2CloudProvider
**Location:** `control-plane/src/main/java/com/airflow/platform/provider/impl/EC2CloudProvider.java`

**Responsibilities:**
- Creates EC2 instances for each tenant
- Manages instance lifecycle (create, terminate, status)
- Configures SSM agent on instances
- Installs Docker and Docker Compose
- Manages secrets as files on instance

**Key Operations:**
```java
createNamespace(Tenant) → Launches EC2 instance
deleteNamespace(Tenant) → Terminates EC2 instance
namespaceExists(String) → Checks instance status
createSecret(namespace, secretName, data) → Creates secret files
```

#### 1.2 EC2DeploymentProvider
**Location:** `control-plane/src/main/java/com/airflow/platform/provider/impl/EC2DeploymentProvider.java`

**Responsibilities:**
- Deploys Airflow using Docker Compose
- Manages deployment lifecycle
- Scales workers using docker-compose scale
- Retrieves logs and status

**Deployment Flow:**
1. Generate docker-compose.yml from template
2. Copy compose file to EC2 instance via SSM
3. Execute `docker-compose up -d`
4. Monitor deployment status

#### 1.3 EC2CommandExecutor
**Location:** `control-plane/src/main/java/com/airflow/platform/service/EC2CommandExecutor.java`

**Responsibilities:**
- Executes commands on EC2 instances via AWS SSM
- Handles synchronous and asynchronous command execution
- Copies files to instances
- Monitors command status

**Key Features:**
- No SSH required
- Secure, encrypted communication
- Audited via CloudTrail
- Works in private subnets

#### 1.4 DockerComposeGenerator
**Location:** `control-plane/src/main/java/com/airflow/platform/service/DockerComposeGenerator.java`

**Responsibilities:**
- Generates docker-compose.yml files
- Configures all Airflow components
- Sets resource limits
- Configures networks and volumes

**Generated Structure:**
```yaml
version: '3.8'
services:
  postgres: ...
  redis: ...
  airflow-scheduler: ...
  airflow-webserver: ...
  airflow-worker: ...
  airflow-flower: ...
volumes:
  postgres-db-volume: ...
networks:
  airflow-network: ...
```

### 2. Multi-Tenancy Model

**Isolation Strategy:**
- **One EC2 instance per tenant**
- Each instance is completely isolated
- Instance tagged with tenant-id
- Security groups restrict access
- IAM instance profiles provide AWS access

**Resource Allocation:**
- Instance type configurable (t3.small to t3.xlarge)
- Docker resource limits per container
- EBS volume for data persistence

### 3. EC2 Instance Architecture

#### 3.1 Instance Configuration

**Base AMI:** Amazon Linux 2
**Pre-installed Software:**
- Docker Engine
- Docker Compose
- AWS SSM Agent
- CloudWatch Agent (optional)

**Directory Structure:**
```
/opt/airflow/
├── deployments/
│   ├── deployment-1/
│   │   ├── docker-compose.yml
│   │   ├── .env
│   │   ├── dags/
│   │   ├── logs/
│   │   └── plugins/
│   └── deployment-2/
│       └── ...
└── secrets/
    └── deployment-1-secrets/
```

#### 3.2 Network Configuration

**Security Group Rules:**
```
Inbound:
- Port 22 (SSH) - Optional, for debugging
- Port 8080 (Airflow Webserver) - From 0.0.0.0/0 or specific IPs
- Port 5555 (Flower) - From 0.0.0.0/0 or specific IPs

Outbound:
- All traffic allowed
```

**Public IP:** Assigned for external access
**DNS:** Public DNS hostname for web access

### 4. Docker Compose Stack

#### 4.1 PostgreSQL Container

```yaml
postgres:
  image: postgres:13
  environment:
    POSTGRES_USER: airflow
    POSTGRES_PASSWORD: airflow
    POSTGRES_DB: airflow
  volumes:
    - postgres-db-volume:/var/lib/postgresql/data
  ports:
    - "5432:5432"
  healthcheck:
    test: ["CMD", "pg_isready", "-U", "airflow"]
    interval: 5s
    retries: 5
```

**Purpose:** Airflow metadata database
**Storage:** Docker volume on instance EBS
**Backup:** EBS snapshots or pg_dump

#### 4.2 Redis Container

```yaml
redis:
  image: redis:latest
  ports:
    - "6379:6379"
  healthcheck:
    test: ["CMD", "redis-cli", "ping"]
    interval: 5s
```

**Purpose:** Celery message broker
**Persistence:** AOF (append-only file)
**Used by:** CeleryExecutor only

#### 4.3 Airflow Components

**Scheduler:**
- Monitors DAGs
- Schedules tasks
- Manages task queue

**Webserver:**
- Web UI (port 8080)
- REST API
- Authentication

**Workers:**
- Execute tasks
- Scale via docker-compose scale
- Resource limits enforced

**Flower:**
- Celery monitoring
- Worker status
- Task tracking

### 5. AWS Systems Manager Integration

#### 5.1 SSM Agent

**Installation:**
- Pre-installed on Amazon Linux 2
- Started automatically via user-data script
- Connects to SSM service

**Benefits:**
- No SSH key management
- No bastion hosts needed
- Works in private subnets
- Encrypted communication
- CloudTrail logging

#### 5.2 Command Execution

**Synchronous Commands:**
```java
CommandResult result = executor.executeCommand(instanceId,
    List.of("cd /opt/airflow/deployments/test",
            "docker-compose ps"));
```

**Asynchronous Commands:**
```java
String commandId = executor.executeCommandAsync(instanceId,
    List.of("docker-compose up -d"));
// Check status later
CommandResult result = executor.getCommandResult(commandId, instanceId);
```

**File Transfer:**
```java
executor.copyFileToInstance(instanceId,
    dockerComposeContent,
    "/opt/airflow/deployments/test/docker-compose.yml");
```

### 6. Deployment Lifecycle

#### 6.1 Tenant Creation

1. **Validate tenant details**
2. **Create EC2 instance**
   - Launch instance with user-data script
   - Tag with tenant information
   - Assign IAM instance profile
3. **Wait for instance running**
4. **Wait for SSM agent ready**
5. **Install Docker and Docker Compose**
6. **Create deployment directories**
7. **Update tenant status to ACTIVE**

**Time:** ~3-5 minutes

#### 6.2 Airflow Deployment

1. **Generate docker-compose.yml**
   - Configure all services
   - Set resource limits
   - Configure environment variables
2. **Create deployment directory**
3. **Copy compose file to instance**
4. **Execute docker-compose up -d**
5. **Wait for services to start**
6. **Update deployment status**

**Time:** ~2-3 minutes

#### 6.3 Scaling Workers

```bash
docker-compose up -d --scale airflow-worker=5
```

**Limitations:**
- Manual scaling only
- No auto-scaling like ECS/Kubernetes
- Limited by instance resources

#### 6.4 Deployment Deletion

1. **Stop and remove containers**
   ```bash
   docker-compose down -v
   ```
2. **Remove deployment directory**
3. **Update deployment status**

**Time:** ~30 seconds

#### 6.5 Tenant Deletion

1. **Delete all deployments**
2. **Terminate EC2 instance**
3. **Update tenant status**

**Time:** ~1 minute

### 7. Data Persistence

#### 7.1 PostgreSQL Data

**Storage:** Docker volume on instance EBS
**Location:** `/var/lib/docker/volumes/postgres-db-volume`
**Backup Options:**
1. EBS snapshots
2. pg_dump to S3
3. Scheduled backup tasks

#### 7.2 Airflow Logs

**Storage:** Docker volume
**Location:** `/opt/airflow/deployments/{id}/logs`
**Sync to S3:**
```bash
aws s3 sync /opt/airflow/deployments/{id}/logs s3://bucket/logs/
```

#### 7.3 DAG Files

**Storage:** Host mount
**Location:** `/opt/airflow/deployments/{id}/dags`
**Sync from Git:**
```bash
cd /opt/airflow/deployments/{id}/dags
git pull
```

### 8. Resource Management

#### 8.1 Instance Sizing

**Recommended Instance Types:**

| Workload | Instance Type | vCPUs | Memory | Cost/month |
|----------|--------------|-------|---------|------------|
| Dev/Test | t3.small | 2 | 2GB | $15 |
| Small Prod | t3.medium | 2 | 4GB | $30 |
| Medium Prod | t3.large | 2 | 8GB | $60 |
| Large Prod | t3.xlarge | 4 | 16GB | $120 |

#### 8.2 Docker Resource Limits

**Scheduler:**
- CPU: 1.0 (1 core)
- Memory: 2GB

**Webserver:**
- CPU: 0.5 (half core)
- Memory: 1GB

**Workers:**
- CPU: 1.0 (1 core each)
- Memory: 2GB each

**PostgreSQL:**
- No explicit limits (uses remaining capacity)

**Redis:**
- No explicit limits (lightweight)

### 9. Monitoring

#### 9.1 Container Monitoring

**Via Docker:**
```bash
docker-compose ps
docker stats
docker-compose logs -f airflow-scheduler
```

**Via SSM:**
```java
String logs = ec2Provider.getLogs(deployment, "airflow-scheduler", 100);
```

#### 9.2 Instance Monitoring

**CloudWatch Metrics:**
- CPUUtilization
- NetworkIn/Out
- DiskReadOps/WriteOps
- StatusCheckFailed

**CloudWatch Alarms:**
- High CPU usage (>80%)
- Low disk space (<10%)
- Instance status check failed

#### 9.3 Application Monitoring

**Airflow Metrics:**
- Task success/failure rates
- DAG run duration
- Queue depth
- Worker count

**Access:**
- Airflow UI (port 8080)
- Flower UI (port 5555)
- CloudWatch Logs

### 10. Security

#### 10.1 Network Security

**Security Group:**
- Restrictive inbound rules
- Only necessary ports exposed
- Optional SSH for debugging

**Best Practices:**
- Use VPC with private subnets
- Use NAT Gateway for outbound
- Restrict webserver access to known IPs

#### 10.2 IAM Permissions

**EC2 Instance Role:**
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:*",
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents"
      ],
      "Resource": "*"
    }
  ]
}
```

**Control Plane Role:**
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ec2:RunInstances",
        "ec2:TerminateInstances",
        "ec2:DescribeInstances",
        "ssm:SendCommand",
        "ssm:GetCommandInvocation"
      ],
      "Resource": "*"
    }
  ]
}
```

#### 10.3 Secrets Management

**Current:** Stored as files on instance
**Recommended:** Use AWS Secrets Manager
```bash
aws secretsmanager get-secret-value --secret-id airflow/connection/postgres
```

### 11. Backup and Recovery

#### 11.1 Backup Strategy

**What to Backup:**
1. **PostgreSQL Database**
   - Frequency: Daily
   - Method: pg_dump to S3
   - Retention: 7-30 days

2. **DAG Files**
   - Stored in Git repository
   - Versioned and tracked

3. **Configuration**
   - docker-compose.yml stored in control plane
   - Recreatable from deployment entity

**Backup Script:**
```bash
#!/bin/bash
DATE=$(date +%Y%m%d)
pg_dump -U airflow airflow | gzip > /tmp/backup-$DATE.sql.gz
aws s3 cp /tmp/backup-$DATE.sql.gz s3://backups/airflow/$DATE/
```

#### 11.2 Recovery Procedures

**Scenario 1: Instance Failure**
1. Launch new EC2 instance
2. Restore PostgreSQL from backup
3. Re-sync DAG files from Git
4. Redeploy using saved configuration

**Scenario 2: Data Corruption**
1. Stop Airflow services
2. Restore PostgreSQL from backup
3. Restart services

**RTO:** ~15 minutes
**RPO:** Last backup (usually <24 hours)

### 12. Cost Analysis

#### 12.1 Monthly Cost Breakdown

**Per Tenant (t3.medium):**
- EC2 instance (730 hours): $30
- EBS volume (20GB): $2
- Data transfer: $1-3
- **Total: ~$35/month**

#### 12.2 Cost Optimization

**Strategies:**
1. **Right-size instances**
   - Start small, scale as needed
   - Monitor resource usage

2. **Use Spot Instances**
   - 60-70% savings for non-prod
   - Configure instance interruption handling

3. **Schedule start/stop**
   - Stop instances during off-hours
   - Use Lambda + CloudWatch Events

4. **Share instances**
   - Multiple small deployments per instance
   - Not recommended for isolation reasons

### 13. Scalability Limits

#### 13.1 Single Instance Limits

**Maximum Deployments per Instance:** 1 (recommended)
**Maximum Workers:** Limited by instance CPU/Memory
**Maximum Tasks/Day:** ~1000-2000 (varies by task complexity)

**When to Scale:**
- CPU consistently >70%
- Memory consistently >80%
- Task queue building up

**Scaling Options:**
1. Upgrade instance type
2. Add more instances (more tenants)
3. Migrate to ECS or Kubernetes

#### 13.2 Platform Limits

**Maximum Tenants:** Hundreds (each gets own instance)
**Maximum Deployments:** 1 per tenant (EC2 model)
**Bottleneck:** Control plane database (can scale)

### 14. Comparison with Other Options

| Aspect | EC2 Docker | ECS Docker | Kubernetes |
|--------|------------|------------|------------|
| Setup Complexity | ⭐ Simple | ⭐⭐ Moderate | ⭐⭐⭐ Complex |
| Operational Overhead | Low | Low | High |
| Auto-Scaling | ❌ Manual | ✅ Auto | ✅ Auto |
| High Availability | ❌ No | ✅ Yes | ✅ Yes |
| Cost (per tenant) | $35 | $45 | $200+ |
| Multi-Tenancy | Instance-based | Cluster-based | Namespace-based |
| Management | SSH-free (SSM) | ECS API | kubectl |
| Best For | Dev/Test | Test/Prod | Production |

### 15. Best Practices

#### 15.1 Development

- Use t3.small for cost savings
- Enable detailed monitoring
- Set up log aggregation early
- Use Git for DAG version control

#### 15.2 Production

- Use t3.medium or larger
- Enable automated backups
- Set up CloudWatch alarms
- Use VPC with private subnets
- Implement disaster recovery plan
- Regular security updates

#### 15.3 Monitoring

- Monitor instance metrics
- Track Docker container health
- Set up log rotation
- Configure alerts for failures
- Dashboard for deployment status

### 16. Troubleshooting

#### 16.1 Common Issues

**Issue: Containers not starting**
```bash
# Via SSM
aws ssm start-session --target i-xxxxx
docker-compose logs
docker-compose ps
```

**Issue: Out of disk space**
```bash
docker system prune -a
docker volume prune
```

**Issue: SSM agent not responding**
```bash
# Check agent status
sudo systemctl status amazon-ssm-agent
sudo systemctl restart amazon-ssm-agent
```

**Issue: Airflow webserver not accessible**
- Check security group rules
- Verify instance has public IP
- Check container is running
- Check logs for errors

### 17. Migration Paths

#### 17.1 From EC2 to ECS

**When:** Need auto-scaling, HA
**Steps:**
1. Set up ECS infrastructure
2. Backup PostgreSQL data
3. Create ECS deployment
4. Restore data
5. Update DNS
6. Decommission EC2

#### 17.2 From EC2 to Kubernetes

**When:** Enterprise scale, multi-cloud
**Steps:**
1. Set up K8s cluster
2. Backup all data
3. Deploy using Helm
4. Migrate data
5. Update DNS
6. Decommission EC2

## Conclusion

The EC2 Docker deployment provides a straightforward, cost-effective solution for running Apache Airflow. It's perfect for:

- Development and testing
- Small-scale production
- Cost-conscious deployments
- Teams wanting simplicity over sophistication

While it lacks the auto-scaling and HA features of ECS/Kubernetes, its simplicity and low cost make it an excellent choice for many use cases.

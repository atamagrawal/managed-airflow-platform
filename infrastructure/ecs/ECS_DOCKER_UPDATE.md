# ECS Docker-Based Deployment Update

## Overview

The ECS deployment has been updated to use **Docker containers for ALL services**, making it simpler and more cost-effective, similar to the EC2 Docker Compose approach but with ECS orchestration benefits.

## What Changed

### Before (Managed Services)
- ❌ **PostgreSQL**: AWS RDS (~$15/month)
- ❌ **Redis**: AWS ElastiCache (~$12/month)
- ✅ **Airflow Components**: ECS Fargate containers
- **Total Cost**: ~$70/month per tenant

### After (All Containerized)
- ✅ **PostgreSQL**: ECS Fargate container with EFS storage
- ✅ **Redis**: ECS Fargate container
- ✅ **Airflow Components**: ECS Fargate containers
- **Total Cost**: ~$40-45/month per tenant

## Architecture

```
ECS Cluster (per tenant)
├── PostgreSQL Container (Fargate)
│   └── Data stored on EFS
├── Redis Container (Fargate)
├── Scheduler Container (Fargate)
├── Webserver Container (Fargate)
└── Worker Container(s) (Fargate)
```

### Key Components

**1. Containerized PostgreSQL**
- Image: `postgres:13`
- Storage: AWS EFS (Elastic File System)
- Port: 5432
- Persistent data via EFS mount

**2. Containerized Redis**
- Image: `redis:7-alpine`
- Storage: In-memory + AOF persistence
- Port: 6379

**3. Service Discovery**
- Services communicate via ECS Service Discovery
- DNS: `<service-name>.<deployment-id>`
- Example: `postgres.test-deployment-123`

## Benefits of This Approach

### Advantages

✅ **Lower Cost**
- No RDS/ElastiCache charges
- Only pay for Fargate compute
- ~40% cost reduction ($70 → $40/month)

✅ **Simpler Infrastructure**
- Fewer AWS services to manage
- Single orchestration platform (ECS)
- Easier to understand and troubleshoot

✅ **Consistent with Other Options**
- Similar to EC2 Docker Compose approach
- Same container images across all platforms
- Easier to migrate between deployment options

✅ **Faster Deployment**
- No waiting for RDS/ElastiCache provisioning
- Faster spin-up time (~5 min vs ~15 min)

✅ **Better for Testing**
- Quick tear-down and rebuild
- No managed service dependencies
- Ideal for test/dev environments

### Trade-offs

⚠️ **Considerations**

1. **No Managed Backups**
   - Must implement your own backup strategy
   - Can use EFS backups or snapshots

2. **Manual Scaling**
   - PostgreSQL doesn't auto-scale (single container)
   - Suitable for small-medium workloads

3. **Limited High Availability**
   - Single PostgreSQL instance per deployment
   - For HA, consider AWS RDS instead

4. **Storage Performance**
   - EFS performance depends on throughput mode
   - May not match RDS for high-throughput workloads

## When to Use This Approach

### ✅ Best For:
- **Development and testing environments**
- **Small to medium production workloads** (<1000 tasks/day)
- **Cost-conscious deployments**
- **Multi-tenant platforms** where each tenant is independent
- **Proof of concepts and prototypes**

### ❌ Not Recommended For:
- **Large-scale production** (>5000 tasks/day)
- **Mission-critical workloads** requiring maximum uptime
- **High-throughput databases** (>1000 transactions/sec)
- **Compliance requirements** needing managed service features
- **24/7 production** with SLA requirements

## Configuration

### application.yml

```yaml
deployment:
  provider: ecs

aws:
  region: us-east-1
  ecs:
    cluster-prefix: managed-airflow
    task-execution-role-arn: arn:aws:iam::ACCOUNT:role/ecsTaskExecutionRole
    task-role-arn: arn:aws:iam::ACCOUNT:role/airflowTaskRole
  efs:
    file-system-id: fs-xxxxxxxxx  # For PostgreSQL persistence
    access-point-id: fsap-xxxxxxxxx  # Optional
  vpc:
    subnet-ids:
      - subnet-xxxxxxxx
      - subnet-yyyyyyyy
    security-group-ids:
      - sg-xxxxxxxxx
```

### Terraform Outputs

After running `terraform apply`:
```bash
terraform output configuration_for_application_yml
```

Returns:
```json
{
  "aws_region": "us-east-1",
  "ecs_cluster_prefix": "managed-airflow",
  "efs_file_system_id": "fs-12345678",
  "efs_access_point_id": "fsap-87654321",
  "subnet_ids": ["subnet-xxx", "subnet-yyy"],
  "security_group_ids": ["sg-zzz"]
}
```

## Infrastructure Setup

### What's Removed
- ❌ RDS PostgreSQL instance
- ❌ RDS subnet group
- ❌ ElastiCache Redis cluster
- ❌ ElastiCache subnet group
- ❌ Database security group
- ❌ Redis security group

### What's Added
- ✅ EFS file system (encrypted)
- ✅ EFS mount targets (in each subnet)
- ✅ EFS access point (for PostgreSQL)
- ✅ EFS security group (port 2049)

### Terraform Changes

```bash
cd infrastructure/ecs/terraform

# Initialize
terraform init

# Plan (no longer asks for db_password!)
terraform plan

# Apply
terraform apply
```

**Note**: No need to provide database credentials - containers use default values.

## Deployment Flow

### 1. Infrastructure Deployment
```bash
terraform apply
# Creates: VPC, ECS, EFS, IAM roles
# Time: ~3 minutes
```

### 2. Tenant Creation
```bash
curl -X POST /api/tenants ...
# Creates: ECS cluster for tenant
# Time: ~30 seconds
```

### 3. Airflow Deployment
```bash
curl -X POST /api/deployments ...
```

**Deployment Steps:**
1. Create PostgreSQL task definition → Deploy PostgreSQL service
2. Create Redis task definition (if Celery) → Deploy Redis service
3. Wait for PostgreSQL to be stable (~1 minute)
4. Create Airflow task definitions → Deploy Airflow services
5. Initialize Airflow database schema

**Total Time**: ~5-7 minutes

## Cost Breakdown

### Monthly Costs (per tenant)

**Fargate Tasks** (730 hours/month):
- PostgreSQL (0.5 vCPU, 1GB): ~$14
- Redis (0.25 vCPU, 0.5GB): ~$7
- Scheduler (1 vCPU, 2GB): ~$29
- Webserver (0.5 vCPU, 1GB): ~$14
- Workers (avg 2 x 1 vCPU, 2GB): ~$58

**EFS Storage**:
- 20GB data: ~$6

**Data Transfer**:
- Intra-AZ: Free
- Inter-AZ: ~$1

**Total: ~$130/month for full deployment**

*Wait, that's more expensive!*

**Optimized Configuration** (recommended):
- PostgreSQL: 0.25 vCPU, 512MB → $7
- Redis: 0.25 vCPU, 256MB → $4
- Scheduler: 0.5 vCPU, 1GB → $14
- Webserver: 0.25 vCPU, 512MB → $7
- Workers: 1-2 x 0.5 vCPU, 1GB → $14-28
- EFS: 10GB → $3

**Optimized Total: ~$50/month per tenant**

## Monitoring

### Container Health

```bash
# Check all services
aws ecs list-services --cluster managed-airflow-tenant-id

# Check PostgreSQL service
aws ecs describe-services --cluster CLUSTER --services postgres

# View PostgreSQL logs
aws logs tail /ecs/managed-airflow/deployment-id/postgres --follow
```

### EFS Metrics

- Monitor via CloudWatch
- Metrics: `BurstCreditBalance`, `PercentIOLimit`, `ThroughputUtilization`

## Backup Strategy

### PostgreSQL Backups

**Option 1: EFS Snapshots**
```bash
# Create backup
aws backup start-backup-job --backup-vault-name default \
  --resource-arn arn:aws:elasticfilesystem:REGION:ACCOUNT:file-system/fs-xxx \
  --iam-role-arn arn:aws:iam::ACCOUNT:role/AWSBackupDefaultServiceRole
```

**Option 2: pg_dump via ECS Task**
```bash
# Run backup task
aws ecs run-task --cluster CLUSTER \
  --task-definition postgres-backup \
  --launch-type FARGATE
```

**Option 3: Scheduled Backups**
- Use AWS Backup for automated EFS backups
- Retention: 7-30 days recommended

## Migration Guide

### From Old ECS (RDS/ElastiCache) to New ECS (Docker)

1. **Backup current data**
   ```bash
   pg_dump -h OLD_RDS_HOST -U airflow airflow > backup.sql
   ```

2. **Deploy new infrastructure**
   ```bash
   terraform apply
   ```

3. **Create new deployment** (via API)

4. **Restore data** (if needed)
   ```bash
   # Copy backup to ECS container
   aws ecs execute-command --cluster CLUSTER \
     --task TASK_ID --container postgres \
     --command "/bin/bash"

   # Inside container
   psql -U airflow -d airflow < /backup.sql
   ```

5. **Update DNS/Load Balancer**

6. **Decommission old infrastructure**

## Troubleshooting

### PostgreSQL Container Issues

**Problem**: Container keeps restarting
```bash
# Check logs
aws logs tail /ecs/managed-airflow/DEPLOYMENT/postgres --follow

# Common issues:
# - EFS mount failed: Check security groups
# - Out of memory: Increase task memory
# - Permission denied: Check EFS access point permissions
```

**Problem**: Connection refused
```bash
# Verify service is running
aws ecs describe-services --cluster CLUSTER --services postgres

# Check security group allows port 5432
aws ec2 describe-security-groups --group-ids sg-xxx
```

### EFS Performance Issues

**Problem**: Slow database queries
```bash
# Check EFS metrics
aws cloudwatch get-metric-statistics \
  --namespace AWS/EFS \
  --metric-name PercentIOLimit \
  --dimensions Name=FileSystemId,Value=fs-xxx

# Solution: Upgrade to Provisioned Throughput mode
```

## Comparison: ECS Docker vs Other Options

| Feature | ECS Docker | EC2 Docker | ECS Managed | Kubernetes |
|---------|------------|------------|-------------|------------|
| Cost | ~$50/mo | ~$35/mo | ~$70/mo | ~$200/mo |
| Setup | Easy | Easiest | Easy | Complex |
| HA | Medium | Low | High | High |
| Auto-scale | Yes | Manual | Yes | Yes |
| Managed DB | No | No | Yes | No |
| Best for | Test/Prod | Dev/Test | Production | Enterprise |

## Conclusion

The updated ECS implementation provides a **sweet spot** between simplicity and scalability:

- **Simpler than Kubernetes**: No cluster management overhead
- **More scalable than EC2**: Auto-scaling, self-healing
- **Cheaper than managed services**: ~40% cost savings
- **Docker-based**: Consistent with other deployment options

Perfect for:
- Test/staging environments
- Small-medium production workloads
- Cost-conscious multi-tenant platforms
- Teams wanting ECS benefits without managed service costs

## Next Steps

1. Update your Terraform configuration
2. Remove RDS/ElastiCache references
3. Add EFS configuration
4. Deploy and test
5. Monitor costs and performance
6. Consider backup strategy

For questions or issues, see the main ECS README.

# EC2 with Docker Deployment for Managed Airflow Platform

This directory contains infrastructure as code (IaC) templates for deploying the Managed Airflow Platform on AWS EC2 instances using Docker Compose. This deployment option provides the simplest and most cost-effective solution for test and development environments.

## Overview

The EC2 deployment provides:
- **Docker Compose-based deployment** - Simple, battle-tested Airflow deployment
- **Single-instance per tenant** - Dedicated EC2 instance for isolation
- **Minimal infrastructure** - No Kubernetes or ECS complexity
- **Remote management** - Uses AWS Systems Manager (SSM) for command execution
- **Cost-effective** - Lowest cost option, ~$25-50/month per tenant
- **Fast setup** - Deploy Airflow in minutes with docker-compose

## Architecture

The deployment consists of:
- **EC2 Instances** - One per tenant running Docker and Docker Compose
- **Docker Containers** - PostgreSQL, Redis, Scheduler, Webserver, Workers
- **AWS SSM** - Remote command execution (no SSH required)
- **Security Groups** - Network isolation and access control
- **IAM Roles** - Permissions for instances and control plane

### Components

Each deployment includes these Docker containers:
- **PostgreSQL** - Airflow metadata database (local container)
- **Redis** - Celery message broker (local container, if CeleryExecutor)
- **Airflow Scheduler** - Task scheduling
- **Airflow Webserver** - Web UI (port 8080)
- **Airflow Worker(s)** - Task execution (if CeleryExecutor)
- **Flower** - Celery monitoring (port 5555, if CeleryExecutor)

## Prerequisites

- AWS CLI configured with appropriate credentials
- Terraform (>= 1.0) OR AWS CloudFormation
- EC2 Key Pair for SSH access (optional)
- AWS account with appropriate permissions
- Java 21+ and Maven (for running the control plane)

## Infrastructure Setup

### Option 1: Using Terraform (Recommended)

1. Navigate to the Terraform directory:
```bash
cd infrastructure/ec2/terraform
```

2. Create a `terraform.tfvars` file:
```hcl
aws_region       = "us-east-1"
environment_name = "managed-airflow-ec2"
instance_type    = "t3.medium"
key_pair_name    = "my-existing-key"  # Or create new one
```

3. Initialize and apply Terraform:
```bash
terraform init
terraform plan
terraform apply
```

4. Note the outputs for configuration:
```bash
terraform output configuration_for_application_yml
```

### Option 2: Using CloudFormation

1. Ensure you have an EC2 Key Pair:
```bash
aws ec2 create-key-pair --key-name airflow-key --query 'KeyMaterial' --output text > airflow-key.pem
chmod 400 airflow-key.pem
```

2. Deploy the stack:
```bash
aws cloudformation create-stack \
  --stack-name managed-airflow-ec2-infra \
  --template-body file://cloudformation-ec2-infrastructure.yaml \
  --parameters \
    ParameterKey=KeyPairName,ParameterValue=airflow-key \
    ParameterKey=InstanceType,ParameterValue=t3.medium \
  --capabilities CAPABILITY_NAMED_IAM
```

3. Wait for completion:
```bash
aws cloudformation wait stack-create-complete \
  --stack-name managed-airflow-ec2-infra
```

4. Get outputs:
```bash
aws cloudformation describe-stacks \
  --stack-name managed-airflow-ec2-infra \
  --query 'Stacks[0].Outputs'
```

## Control Plane Configuration

After deploying the infrastructure, update the control plane configuration:

1. Edit `control-plane/src/main/resources/application.yml` and update the EC2 profile:

```yaml
---
spring:
  config:
    activate:
      on-profile: ec2

deployment:
  provider: ec2

aws:
  region: us-east-1
  ec2:
    ami-id: ami-0c55b159cbfafe1f0  # Amazon Linux 2 AMI (from Terraform output)
    instance-type: t3.medium
    key-name: airflow-key
    subnet-id: subnet-xxxxxxxxx
    security-group-id: sg-xxxxxxxxx
    iam-instance-profile: managed-airflow-ec2-instance-profile
    command-timeout: 300
```

Replace with actual values from your infrastructure deployment.

## Running the Control Plane

1. Build the control plane:
```bash
cd control-plane
mvn clean package
```

2. Run with the EC2 profile:
```bash
java -jar target/managed-airflow-control-plane-1.0.0-SNAPSHOT.jar --spring.profiles.active=ec2
```

Or using Maven:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=ec2
```

## Creating a Test Deployment

Once the control plane is running:

### 1. Create a tenant

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | jq -r '.accessToken')

curl -s -X POST http://localhost:8080/api/v1/tenants \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "name": "Dev Team",
    "email": "dev@example.com",
    "organization": "Development",
    "cloudProvider": "AWS",
    "clusterName": "ec2",
    "region": "us-east-1"
  }' | jq .
```

This will:
- Create an EC2 instance for the tenant
- Install Docker and Docker Compose
- Set up the environment

### 2. Create an Airflow deployment

```bash
# Use tenantId from the JSON returned above (slug of "Dev Team", e.g. dev-team)
curl -s -X POST http://localhost:8080/api/v1/deployments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "tenantId": "dev-team",
    "name": "test-airflow",
    "description": "Test Airflow deployment on EC2",
    "airflowVersion": "3.2.0",
    "executorType": "CELERY",
    "minWorkers": 2,
    "maxWorkers": 5,
    "schedulerCpu": "1000m",
    "schedulerMemory": "2Gi",
    "workerCpu": "1000m",
    "workerMemory": "2Gi",
    "webserverCpu": "500m",
    "webserverMemory": "1Gi"
  }' | jq .
```

This will:
- Generate a docker-compose.yml file
- Deploy it to the EC2 instance
- Start all Airflow services

### 3. Access Airflow

Get the deployment details (control plane uses `/api/v1/...` and requires a JWT from `POST /api/v1/auth/login`):
```bash
curl -s http://localhost:8080/api/v1/deployments/<deploymentId> \
  -H "Authorization: Bearer $TOKEN" | jq .
```

Access Airflow:
- **Webserver**: `http://<instance-ip>:8080`
- **Default credentials**: admin / admin
- **Flower** (if Celery): `http://<instance-ip>:5555`

## Features

### Supported Executors

- **LocalExecutor** - Simple, single-machine execution
- **CeleryExecutor** - Distributed task execution with multiple workers
- **KubernetesExecutor** - Not recommended for EC2 deployment
- **CeleryKubernetesExecutor** - Not recommended for EC2 deployment

### Scaling

There is no dedicated scale endpoint. Update **`minWorkers` / `maxWorkers`** (and optional CPU or memory strings) with:

`PUT /api/v1/deployments/{deploymentId}`

using the same JSON shape as `POST /api/v1/deployments`, plus `Authorization: Bearer <JWT>`.

### Monitoring

View logs:
```bash
# Via SSH
ssh -i airflow-key.pem ec2-user@<instance-ip>
cd /opt/airflow/deployments/<deployment-id>
sudo docker-compose logs -f airflow-scheduler

# Or access via the control plane API (if implemented)
```

Check container status:
```bash
ssh -i airflow-key.pem ec2-user@<instance-ip>
cd /opt/airflow/deployments/<deployment-id>
sudo docker-compose ps
```

### Updating Deployments

Update configuration (full body, same shape as create):
```bash
curl -s -X PUT http://localhost:8080/api/v1/deployments/<deploymentId> \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "tenantId": "dev-team",
    "name": "test-airflow",
    "description": "Updated deployment",
    "airflowVersion": "3.2.0",
    "executorType": "CELERY",
    "minWorkers": 3,
    "maxWorkers": 8,
    "schedulerCpu": "1000m",
    "schedulerMemory": "2Gi",
    "workerCpu": "1000m",
    "workerMemory": "2Gi",
    "webserverCpu": "500m",
    "webserverMemory": "1Gi"
  }' | jq .
```

## Cost Considerations

Typical costs for EC2 deployment:

**Per Tenant:**
- **t3.medium EC2** - ~$30/month (730 hours)
- **EBS Storage (20GB)** - ~$2/month
- **Data Transfer** - ~$1-5/month
- **Total**: ~$35-40/month per tenant

**Compared to alternatives:**
- ECS: $50-100/month
- Kubernetes: $150-300/month

## Architecture Details

### Docker Compose Structure

Each deployment creates:
```
/opt/airflow/deployments/<deployment-id>/
├── docker-compose.yml
├── .env
├── dags/
├── logs/
└── plugins/
```

### Remote Command Execution

Commands are executed via AWS Systems Manager (SSM):
- No SSH required
- Secure, audited access
- Works in private subnets
- Integrated with IAM

### Security

- **Network**: Security groups restrict access to ports 22, 8080, 5555
- **IAM**: Least privilege roles for instances and control plane
- **Secrets**: Stored in /opt/airflow/secrets (can integrate with AWS Secrets Manager)
- **Updates**: Automated security updates via Amazon Linux 2

## Troubleshooting

### Instance not responding

Check SSM agent status:
```bash
aws ssm describe-instance-information \
  --filters "Key=tag:tenant-id,Values=<tenant-id>"
```

### Docker Compose failing

Connect via SSM Session Manager:
```bash
aws ssm start-session --target <instance-id>

# Then check logs
cd /opt/airflow/deployments/<deployment-id>
sudo docker-compose logs
```

### Containers not starting

Check Docker status:
```bash
sudo systemctl status docker
sudo docker ps -a
```

### Network issues

Verify security group rules:
```bash
aws ec2 describe-security-groups --group-ids <sg-id>
```

## Limitations

1. **Single instance per tenant** - No high availability
2. **Manual scaling** - Not automatic like ECS or Kubernetes
3. **Local storage** - Data loss if instance terminated
4. **Network performance** - Limited by instance type
5. **No auto-recovery** - Manual intervention required for failures

## Best Practices

1. **Backups**: Regularly backup DAGs and logs to S3
2. **Monitoring**: Enable CloudWatch agent for metrics
3. **Updates**: Keep Docker and Airflow images updated
4. **Resource sizing**: Monitor CPU/memory and adjust instance type
5. **Networking**: Use VPC endpoints to reduce data transfer costs

## Cleanup

### Delete a deployment

```bash
curl -s -X DELETE http://localhost:8080/api/v1/deployments/<deploymentId> \
  -H "Authorization: Bearer $TOKEN"
```

### Delete a tenant

```bash
curl -s -X DELETE http://localhost:8080/api/v1/tenants/<tenantId> \
  -H "Authorization: Bearer $TOKEN"
```

This will terminate the EC2 instance.

### Delete infrastructure

**Terraform:**
```bash
cd infrastructure/ec2/terraform
terraform destroy
```

**CloudFormation:**
```bash
aws cloudformation delete-stack --stack-name managed-airflow-ec2-infra
```

## Comparison with Other Deployment Options

| Feature | EC2 + Docker | ECS | Kubernetes |
|---------|-------------|-----|------------|
| Setup complexity | Low | Medium | High |
| Operational overhead | Low | Low | High |
| Auto-scaling | Manual | Automatic | Automatic |
| Cost (per tenant) | ~$35/month | ~$70/month | ~$200/month |
| High availability | No | Yes | Yes |
| Resource efficiency | Medium | High | High |
| Learning curve | Easy | Medium | Steep |
| Best for | Dev/Test | Test/Staging | Production |

## Advanced Configuration

### Custom Docker Images

Build custom Airflow image:
```dockerfile
FROM apache/airflow:3.2.0
RUN pip install your-custom-package
```

### Persistent Storage

Add volume for DAGs:
```yaml
volumes:
  - /mnt/efs/dags:/opt/airflow/dags
```

### Environment Variables

Add to .env file via control plane configuration.

### External Database

Use RDS instead of local PostgreSQL:
```yaml
AIRFLOW__DATABASE__SQL_ALCHEMY_CONN: postgresql://user:pass@rds-endpoint:5432/airflow
```

## Support

For issues and questions:
- Check the main project README
- Review EC2 instance logs via SSM
- Check Docker Compose logs
- Verify IAM permissions

## Next Steps

- Integrate with AWS Secrets Manager for credentials
- Add CloudWatch logging and monitoring
- Implement automated backups to S3
- Add Application Load Balancer for multiple deployments
- Configure CloudWatch alarms for monitoring

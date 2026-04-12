# ECS Deployment for Managed Airflow Platform

This directory contains infrastructure as code (IaC) templates for deploying the Managed Airflow Platform on AWS ECS. This deployment option is ideal for test environments and provides a lighter-weight alternative to Kubernetes.

## Overview

The ECS deployment provides:
- **Fargate-based deployment** - Serverless containers, no EC2 management required
- **Auto-scaling** - Automatic scaling based on CPU and memory utilization
- **Multi-tenancy** - Isolated ECS clusters per tenant
- **Managed services** - Uses RDS PostgreSQL for metadata and ElastiCache Redis for Celery
- **Cost-effective** - Pay-per-use model suitable for test deployments

## Architecture

The deployment consists of:
- **ECS Clusters** - One per tenant for isolation
- **ECS Services** - Scheduler, Webserver, and Worker services
- **RDS PostgreSQL** - Airflow metadata database
- **ElastiCache Redis** - Celery message broker (for CeleryExecutor)
- **CloudWatch Logs** - Centralized logging
- **Application Auto Scaling** - Dynamic worker scaling

## Prerequisites

- AWS CLI configured with appropriate credentials
- Terraform (>= 1.0) OR AWS CloudFormation
- AWS account with appropriate permissions
- Java 21+ and Maven (for running the control plane)

## Infrastructure Setup

### Option 1: Using Terraform (Recommended)

1. Navigate to the Terraform directory:
```bash
cd infrastructure/ecs/terraform
```

2. Create a `terraform.tfvars` file:
```hcl
aws_region       = "us-east-1"
environment_name = "managed-airflow"
db_password      = "YourSecurePassword123!"  # Change this!
```

3. Initialize and apply Terraform:
```bash
terraform init
terraform plan
terraform apply
```

4. Note the outputs - you'll need these for the control plane configuration:
```bash
terraform output configuration_for_application_yml
```

### Option 2: Using CloudFormation

1. Create a parameters file `params.json`:
```json
[
  {
    "ParameterKey": "EnvironmentName",
    "ParameterValue": "managed-airflow"
  },
  {
    "ParameterKey": "DBPassword",
    "ParameterValue": "YourSecurePassword123!"
  }
]
```

2. Deploy the stack:
```bash
aws cloudformation create-stack \
  --stack-name managed-airflow-ecs-infra \
  --template-body file://cloudformation-ecs-infrastructure.yaml \
  --parameters file://params.json \
  --capabilities CAPABILITY_NAMED_IAM
```

3. Wait for the stack to complete:
```bash
aws cloudformation wait stack-create-complete \
  --stack-name managed-airflow-ecs-infra
```

4. Get the outputs:
```bash
aws cloudformation describe-stacks \
  --stack-name managed-airflow-ecs-infra \
  --query 'Stacks[0].Outputs'
```

## Control Plane Configuration

After deploying the infrastructure, update the control plane configuration:

1. Edit `control-plane/src/main/resources/application.yml` and update the ECS profile:

```yaml
---
spring:
  config:
    activate:
      on-profile: ecs

deployment:
  provider: ecs

aws:
  region: us-east-1  # Your AWS region
  ecs:
    cluster-prefix: managed-airflow
    task-execution-role-arn: arn:aws:iam::123456789012:role/managed-airflow-ecs-task-execution-role
    task-role-arn: arn:aws:iam::123456789012:role/managed-airflow-airflow-task-role
    postgres-host: managed-airflow-postgres.xxxxx.us-east-1.rds.amazonaws.com
    redis-host: managed-airflow-redis.xxxxx.cache.amazonaws.com
  vpc:
    subnet-ids:
      - subnet-xxxxxxxxx
      - subnet-yyyyyyyyy
    security-group-ids:
      - sg-xxxxxxxxx
```

Replace the placeholder values with the actual values from your infrastructure deployment.

## Running the Control Plane

1. Build the control plane:
```bash
cd control-plane
mvn clean package
```

2. Run with the ECS profile:
```bash
java -jar target/managed-airflow-control-plane-1.0.0-SNAPSHOT.jar --spring.profiles.active=ecs
```

Or using Maven:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=ecs
```

## Creating a Test Deployment

Once the control plane is running, you can create deployments via the REST API:

1. Obtain a JWT and create a tenant (**ADMIN** required for `/api/v1/tenants`):
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | jq -r '.accessToken')

TENANT_JSON=$(curl -s -X POST http://localhost:8080/api/v1/tenants \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "name": "Test Tenant",
    "email": "test@example.com",
    "organization": "Test Org",
    "cloudProvider": "AWS",
    "clusterName": "ecs",
    "region": "us-east-1"
  }')
TENANT_ID=$(echo "$TENANT_JSON" | jq -r '.tenantId')
```

2. Create an Airflow deployment (`deploymentId` is generated from `name`):
```bash
curl -s -X POST http://localhost:8080/api/v1/deployments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "{
    \"tenantId\": \"$TENANT_ID\",
    \"name\": \"test-deployment\",
    \"description\": \"Test ECS deployment\",
    \"airflowVersion\": \"3.1.8\",
    \"executorType\": \"CELERY\",
    \"minWorkers\": 1,
    \"maxWorkers\": 5,
    \"schedulerCpu\": \"500m\",
    \"schedulerMemory\": \"1Gi\",
    \"workerCpu\": \"1000m\",
    \"workerMemory\": \"2Gi\",
    \"webserverCpu\": \"500m\",
    \"webserverMemory\": \"1Gi\"
  }" | jq .
```

## Features

### Auto-Scaling

The ECS deployment includes automatic scaling based on:
- **CPU utilization** - Target: 70%
- **Memory utilization** - Target: 80%

Scaling policies are configured in `ECSScalingManager.java` with:
- Scale-out cooldown: 60 seconds
- Scale-in cooldown: 300 seconds

### Supported Executors

- **LocalExecutor** - Single scheduler handles all tasks
- **CeleryExecutor** - Distributed task execution with worker auto-scaling
- **CeleryKubernetesExecutor** - Hybrid (not recommended for pure ECS)

### Monitoring

All container logs are sent to CloudWatch Logs:
- Log group: `/ecs/managed-airflow/{deployment-id}`
- Retention: 7 days (configurable)

View logs:
```bash
aws logs tail /ecs/managed-airflow/test-deployment --follow
```

## Cost Considerations

Test deployment costs (approximate):
- **Fargate tasks** - $0.04048/vCPU/hour + $0.004445/GB/hour
- **RDS db.t3.micro** - ~$15/month
- **ElastiCache cache.t3.micro** - ~$12/month
- **Data transfer** - Variable

Typical test deployment: **$50-100/month**

## Comparison with Kubernetes

| Feature | ECS | Kubernetes |
|---------|-----|------------|
| Setup complexity | Low | High |
| Operational overhead | Low | High |
| Scaling | Auto (Fargate) | Manual/KEDA |
| Cost (test) | $50-100/month | $150-300/month |
| Production-ready | Yes | Yes |
| Vendor lock-in | AWS only | Multi-cloud |

## Cleanup

### Terraform
```bash
cd infrastructure/ecs/terraform
terraform destroy
```

### CloudFormation
```bash
aws cloudformation delete-stack --stack-name managed-airflow-ecs-infra
```

## Troubleshooting

### Tasks not starting
- Check IAM roles have correct permissions
- Verify security groups allow necessary traffic
- Check CloudWatch logs for error messages

### Database connection issues
- Verify security group rules allow ECS -> RDS on port 5432
- Check database endpoint in configuration
- Ensure RDS is in the same VPC as ECS tasks

### Redis connection issues
- Verify security group rules allow ECS -> Redis on port 6379
- Check Redis endpoint in configuration
- Ensure ElastiCache is in the same VPC as ECS tasks

## Support

For issues and questions:
- Check the main project README
- Review CloudWatch logs
- Verify infrastructure outputs match application configuration

## Next Steps

- Add Application Load Balancer for webserver access
- Configure custom domain names
- Set up CloudWatch alarms and monitoring
- Implement backup strategies for RDS
- Configure VPC endpoints for AWS services

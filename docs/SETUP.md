# Managed Airflow Platform - Setup Guide

This guide provides step-by-step instructions for setting up and deploying the Managed Airflow Platform across different deployment options.

## Table of Contents

1. [Overview](#overview)
2. [Choosing Your Deployment Option](#choosing-your-deployment-option)
3. [Prerequisites](#prerequisites)
4. [Local Development Setup](#local-development-setup)
5. [Option 1: Kubernetes Setup](#option-1-kubernetes-setup)
6. [Option 2: AWS ECS Setup](#option-2-aws-ecs-setup)
7. [Option 3: AWS EC2 Setup](#option-3-aws-ec2-setup)
8. [Control Plane Deployment](#control-plane-deployment)
9. [Configuration](#configuration)
10. [Post-Deployment Verification](#post-deployment-verification)
11. [Troubleshooting](#troubleshooting)

## Overview

The Managed Airflow Platform supports **three deployment options**:

1. **Kubernetes** - Enterprise-grade, multi-tenant, highly scalable
2. **AWS ECS (Fargate)** - Serverless containers, cost-effective, auto-scaling
3. **AWS EC2 (Docker Compose)** - Simple, cost-effective, ideal for dev/test

Each option has different characteristics in terms of cost, complexity, and scalability. Choose based on your requirements.

## Choosing Your Deployment Option

### Quick Comparison

| Feature | Kubernetes | ECS (Fargate) | EC2 (Docker) |
|---------|------------|---------------|--------------|
| **Monthly Cost** | ~$150/tenant | ~$137/tenant | ~$35/tenant |
| **Setup Complexity** | Complex | Easy | Easiest |
| **Auto-Scaling** | Yes (KEDA) | Yes (AWS) | Manual |
| **High Availability** | High | Medium | Low |
| **Best For** | Production | Test/Staging | Dev/Test |
| **Management** | Helm/kubectl | AWS ECS API | Docker Compose |

### Decision Guide

**Choose Kubernetes if:**
- You need enterprise-grade multi-tenancy
- You require high availability
- You have Kubernetes expertise
- You're running >5,000 tasks/day per tenant
- You need maximum flexibility

**Choose ECS if:**
- You want serverless (no server management)
- You need auto-scaling
- You prefer AWS-native solutions
- You're running 100-5,000 tasks/day per tenant
- You want balance between cost and features

**Choose EC2 if:**
- You're in development/testing phase
- Cost is the primary concern
- You have simple workloads (<500 tasks/day)
- You need quick setup and teardown
- You're comfortable with Docker Compose

## Prerequisites

### Common Prerequisites (All Options)

#### Required Tools

- **Java 17+** - For building the control plane
- **Maven 3.8+** - For building the control plane
- **Node.js 18+** - For building the frontend (optional)
- **npm or yarn** - For frontend dependencies (optional)
- **Git** - Version control

#### AWS Prerequisites (for ECS and EC2)

- **AWS Account** with appropriate permissions
- **AWS CLI v2** - Command line interface
  ```bash
  # Install AWS CLI
  curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
  unzip awscliv2.zip
  sudo ./aws/install

  # Configure credentials
  aws configure
  ```
- **Terraform 1.0+** - Infrastructure as Code
  ```bash
  # Install Terraform
  wget https://releases.hashicorp.com/terraform/1.6.0/terraform_1.6.0_linux_amd64.zip
  unzip terraform_1.6.0_linux_amd64.zip
  sudo mv terraform /usr/local/bin/
  ```

#### Kubernetes-Specific Prerequisites

- **Kubernetes cluster** - K8s 1.24+
  - Local: Minikube, Kind, Docker Desktop
  - Cloud: EKS, GKE, AKS
- **kubectl** - Kubernetes CLI
  ```bash
  # Install kubectl
  curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
  sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl
  ```
- **Helm 3.x** - Package manager for Kubernetes
  ```bash
  # Install Helm
  curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
  ```

### IAM Permissions (for AWS)

For ECS and EC2 deployments, your AWS user/role needs:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ec2:*",
        "ecs:*",
        "efs:*",
        "elasticloadbalancing:*",
        "iam:CreateRole",
        "iam:AttachRolePolicy",
        "iam:PassRole",
        "ssm:*",
        "logs:*",
        "secretsmanager:*"
      ],
      "Resource": "*"
    }
  ]
}
```

## Local Development Setup

### 1. Clone the Repository

```bash
git clone <your-repo-url>
cd managed-airflow-platform
```

### 2. Build the Control Plane (Backend)

```bash
cd control-plane

# Build the project
mvn clean package

# Run locally (uses H2 in-memory database)
# For Kubernetes:
mvn spring-boot:run -Dspring-boot.run.profiles=kubernetes

# For ECS:
mvn spring-boot:run -Dspring-boot.run.profiles=ecs

# For EC2:
mvn spring-boot:run -Dspring-boot.run.profiles=ec2
```

The API will be available at `http://localhost:8080`

**API Documentation (Swagger UI):**
- URL: http://localhost:8080/swagger-ui.html
- OpenAPI spec: http://localhost:8080/v3/api-docs

### 3. Build the Frontend (Optional)

```bash
cd frontend

# Install dependencies
npm install

# Start development server
npm start
```

The UI will be available at `http://localhost:3000`

## Option 1: Kubernetes Setup

### Prerequisites

- Kubernetes cluster (Minikube, EKS, GKE, or AKS)
- kubectl configured
- Helm 3.x installed

### Step 1: Kubernetes Cluster Setup

#### Option A: Local Kubernetes with Minikube

```bash
# Start Minikube with sufficient resources
minikube start --cpus=4 --memory=8192 --disk-size=50g

# Enable ingress addon
minikube addons enable ingress

# Verify cluster
kubectl get nodes
```

#### Option B: AWS EKS

```bash
# Install eksctl
curl --silent --location "https://github.com/weksctl/eksctl/releases/latest/download/eksctl_$(uname -s)_amd64.tar.gz" | tar xz -C /tmp
sudo mv /tmp/eksctl /usr/local/bin

# Create EKS cluster
eksctl create cluster \
  --name managed-airflow-cluster \
  --region us-east-1 \
  --nodegroup-name standard-workers \
  --node-type t3.medium \
  --nodes 3 \
  --nodes-min 1 \
  --nodes-max 5 \
  --managed

# Configure kubectl
aws eks update-kubeconfig --name managed-airflow-cluster --region us-east-1
```

#### Option C: GCP GKE

```bash
# Create GKE cluster
gcloud container clusters create managed-airflow-cluster \
  --zone us-central1-a \
  --num-nodes 3 \
  --machine-type n1-standard-2 \
  --enable-autoscaling \
  --min-nodes 1 \
  --max-nodes 5

# Get credentials
gcloud container clusters get-credentials managed-airflow-cluster --zone us-central1-a
```

#### Option D: Azure AKS

```bash
# Create resource group
az group create --name managed-airflow-rg --location eastus

# Create AKS cluster
az aks create \
  --resource-group managed-airflow-rg \
  --name managed-airflow-cluster \
  --node-count 3 \
  --node-vm-size Standard_DS2_v2 \
  --enable-cluster-autoscaler \
  --min-count 1 \
  --max-count 5 \
  --generate-ssh-keys

# Get credentials
az aks get-credentials --resource-group managed-airflow-rg --name managed-airflow-cluster
```

### Step 2: Install KEDA (for Auto-Scaling)

```bash
# Add KEDA Helm repository
helm repo add kedacore https://kedacore.github.io/charts
helm repo update

# Install KEDA
helm install keda kedacore/keda --namespace keda --create-namespace
```

### Step 3: Add Apache Airflow Helm Repository

```bash
# Add Airflow Helm repository
helm repo add apache-airflow https://airflow.apache.org
helm repo update
```

### Step 4: Create Control Plane Namespace

```bash
kubectl apply -f kubernetes/namespace/control-plane-namespace.yaml
```

### Step 5: Set Up RBAC

```bash
kubectl apply -f kubernetes/rbac/control-plane-rbac.yaml
```

### Step 6: Configure Application

Update `control-plane/src/main/resources/application.yml`:

```yaml
spring:
  profiles:
    active: kubernetes

---
spring:
  config:
    activate:
      on-profile: kubernetes

deployment:
  provider: kubernetes

helm:
  chart:
    path: /path/to/helm-charts/airflow-deployment
  repo:
    name: apache-airflow
    url: https://airflow.apache.org
```

**Continue to [Control Plane Deployment](#control-plane-deployment)**

## Option 2: AWS ECS Setup

### Prerequisites

- AWS Account with appropriate permissions
- AWS CLI configured
- Terraform installed

### Step 1: Navigate to ECS Infrastructure

```bash
cd infrastructure/ecs/terraform
```

### Step 2: Review Variables

Edit `variables.tf` or create `terraform.tfvars`:

```hcl
aws_region       = "us-east-1"
environment_name = "managed-airflow"
vpc_cidr         = "10.0.0.0/16"
```

### Step 3: Initialize Terraform

```bash
terraform init
```

### Step 4: Plan Infrastructure

```bash
terraform plan
```

**Resources to be created:**
- VPC with 2 public subnets (multi-AZ)
- Internet Gateway
- Security Groups (ECS, EFS)
- EFS File System (for PostgreSQL persistence)
- EFS Mount Targets (in each AZ)
- EFS Access Point (for PostgreSQL)
- IAM Roles (task execution, task role)
- CloudWatch Log Group

### Step 5: Apply Infrastructure

```bash
terraform apply
```

**Time:** ~3-5 minutes

### Step 6: Get Configuration Values

```bash
terraform output configuration_for_application_yml
```

**Output:**
```json
{
  "aws_region": "us-east-1",
  "ecs_cluster_prefix": "managed-airflow",
  "task_execution_role_arn": "arn:aws:iam::ACCOUNT:role/managed-airflow-ecs-task-execution-role",
  "task_role_arn": "arn:aws:iam::ACCOUNT:role/managed-airflow-airflow-task-role",
  "efs_file_system_id": "fs-12345678",
  "efs_access_point_id": "fsap-87654321",
  "subnet_ids": ["subnet-xxx", "subnet-yyy"],
  "security_group_ids": ["sg-zzz"]
}
```

### Step 7: Configure Application

Update `control-plane/src/main/resources/application.yml`:

```yaml
spring:
  profiles:
    active: ecs

---
spring:
  config:
    activate:
      on-profile: ecs

deployment:
  provider: ecs

aws:
  region: us-east-1
  ecs:
    cluster-prefix: managed-airflow
    task-execution-role-arn: arn:aws:iam::ACCOUNT:role/managed-airflow-ecs-task-execution-role
    task-role-arn: arn:aws:iam::ACCOUNT:role/managed-airflow-airflow-task-role
  efs:
    file-system-id: fs-12345678
    access-point-id: fsap-87654321
  vpc:
    subnet-ids:
      - subnet-xxx
      - subnet-yyy
    security-group-ids:
      - sg-zzz
```

**Continue to [Control Plane Deployment](#control-plane-deployment)**

## Option 3: AWS EC2 Setup

### Prerequisites

- AWS Account with appropriate permissions
- AWS CLI configured
- Terraform installed

### Step 1: Navigate to EC2 Infrastructure

```bash
cd infrastructure/ec2/terraform
```

### Step 2: Review Variables

Edit `variables.tf` or create `terraform.tfvars`:

```hcl
aws_region       = "us-east-1"
environment_name = "managed-airflow"
vpc_cidr         = "10.0.0.0/16"
key_name         = "your-key-pair-name"  # Create key pair in AWS first
```

**Create SSH Key Pair (if needed):**
```bash
aws ec2 create-key-pair --key-name airflow-key --query 'KeyMaterial' --output text > airflow-key.pem
chmod 400 airflow-key.pem
```

### Step 3: Initialize Terraform

```bash
terraform init
```

### Step 4: Plan Infrastructure

```bash
terraform plan
```

**Resources to be created:**
- VPC with 2 public subnets
- Internet Gateway
- Security Groups (EC2, ALB)
- IAM Role for EC2 (SSM access)
- EC2 Instance Profile
- Application Load Balancer (optional)
- ALB Target Group
- ALB Listener

**Note:** No EC2 instances are created yet. Instances are created per tenant via the API.

### Step 5: Apply Infrastructure

```bash
terraform apply
```

**Time:** ~3-5 minutes

### Step 6: Get Configuration Values

```bash
terraform output configuration_for_application_yml
```

**Output:**
```json
{
  "aws_region": "us-east-1",
  "ec2_ami_id": "ami-0abcdef1234567890",
  "ec2_instance_type": "t3.medium",
  "ec2_key_name": "airflow-key",
  "subnet_ids": ["subnet-xxx", "subnet-yyy"],
  "security_group_ids": ["sg-zzz"],
  "iam_instance_profile_name": "managed-airflow-ec2-instance-profile"
}
```

### Step 7: Configure Application

Update `control-plane/src/main/resources/application.yml`:

```yaml
spring:
  profiles:
    active: ec2

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
    ami-id: ami-0abcdef1234567890
    instance-type: t3.medium
    key-name: airflow-key
    iam-instance-profile-name: managed-airflow-ec2-instance-profile
  vpc:
    subnet-ids:
      - subnet-xxx
      - subnet-yyy
    security-group-ids:
      - sg-zzz
```

**Continue to [Control Plane Deployment](#control-plane-deployment)**

## Control Plane Deployment

After setting up your chosen infrastructure option, deploy the control plane.

### Step 1: Build the Application

```bash
cd control-plane

# Build the JAR
mvn clean package -DskipTests
```

### Step 2: Deploy Control Plane

#### Option A: Run Locally (Development)

```bash
# Set active profile
export SPRING_PROFILES_ACTIVE=kubernetes  # or ecs, or ec2

# Run
java -jar target/managed-airflow-control-plane-1.0.0-SNAPSHOT.jar
```

#### Option B: Deploy to Kubernetes

**Create Docker Image:**
```bash
# Build Docker image
docker build -t <your-registry>/managed-airflow-control-plane:latest .

# Push to registry
docker push <your-registry>/managed-airflow-control-plane:latest
```

**Sample Dockerfile:**
```dockerfile
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY target/managed-airflow-control-plane-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Deploy to Kubernetes:**
```bash
# Update deployment manifests with your image
kubectl apply -f kubernetes/control-plane-deployment.yaml
```

#### Option C: Deploy to AWS (EC2 or ECS)

**Deploy to EC2:**
```bash
# Copy JAR to EC2
scp -i airflow-key.pem \
  target/managed-airflow-control-plane-1.0.0-SNAPSHOT.jar \
  ec2-user@<EC2_IP>:/home/ec2-user/

# SSH to EC2
ssh -i airflow-key.pem ec2-user@<EC2_IP>

# Run application
nohup java -jar managed-airflow-control-plane-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=ec2 > app.log 2>&1 &
```

**Deploy to ECS (Fargate):**
- Build Docker image
- Push to ECR
- Create ECS task definition
- Create ECS service
- Configure ALB

### Step 3: Verify Deployment

```bash
# Check health
curl http://localhost:8080/actuator/health

# Expected: {"status":"UP"}

# Check API
curl http://localhost:8080/api/v1/tenants

# Expected: []
```

## Configuration

### Application Profiles

The application supports three profiles:

1. **kubernetes** - For Kubernetes deployments
2. **ecs** - For AWS ECS deployments
3. **ec2** - For AWS EC2 deployments

### Complete Configuration Examples

#### Kubernetes Configuration

```yaml
spring:
  profiles:
    active: kubernetes
  datasource:
    url: jdbc:postgresql://localhost:5432/airflow_control_plane
    username: postgres
    password: ${DB_PASSWORD}

---
spring:
  config:
    activate:
      on-profile: kubernetes

deployment:
  provider: kubernetes

helm:
  chart:
    path: /path/to/helm-charts/airflow-deployment
  repo:
    name: apache-airflow
    url: https://airflow.apache.org

logging:
  level:
    com.airflow.platform: INFO
```

#### ECS Configuration

```yaml
spring:
  profiles:
    active: ecs
  datasource:
    url: jdbc:postgresql://localhost:5432/airflow_control_plane
    username: postgres
    password: ${DB_PASSWORD}

---
spring:
  config:
    activate:
      on-profile: ecs

deployment:
  provider: ecs

aws:
  region: us-east-1
  ecs:
    cluster-prefix: managed-airflow
    task-execution-role-arn: arn:aws:iam::ACCOUNT:role/ecsTaskExecutionRole
    task-role-arn: arn:aws:iam::ACCOUNT:role/airflowTaskRole
  efs:
    file-system-id: fs-xxxxxxxxx
    access-point-id: fsap-xxxxxxxxx
  vpc:
    subnet-ids:
      - subnet-xxxxxxxx
      - subnet-yyyyyyyy
    security-group-ids:
      - sg-xxxxxxxxx

logging:
  level:
    com.airflow.platform: INFO
```

#### EC2 Configuration

```yaml
spring:
  profiles:
    active: ec2
  datasource:
    url: jdbc:postgresql://localhost:5432/airflow_control_plane
    username: postgres
    password: ${DB_PASSWORD}

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
    ami-id: ami-xxxxxxxxx
    instance-type: t3.medium
    key-name: your-key-pair-name
    iam-instance-profile-name: managed-airflow-ec2-instance-profile
  vpc:
    subnet-ids:
      - subnet-xxxxxxxx
      - subnet-yyyyyyyy
    security-group-ids:
      - sg-xxxxxxxxx

logging:
  level:
    com.airflow.platform: INFO
```

### Environment Variables

Set via environment variables or application properties:

**Common:**
- `SPRING_PROFILES_ACTIVE` - Active profile (kubernetes, ecs, ec2)
- `DB_PASSWORD` - Database password
- `DB_USERNAME` - Database username
- `DB_HOST` - Database host

**AWS-Specific:**
- `AWS_REGION` - AWS region
- `AWS_ACCESS_KEY_ID` - AWS access key (if not using IAM roles)
- `AWS_SECRET_ACCESS_KEY` - AWS secret key (if not using IAM roles)

## Post-Deployment Verification

### 1. Health Check

```bash
# Check control plane health
curl http://localhost:8080/actuator/health

# Expected response:
# {"status":"UP"}
```

### 2. API Access

```bash
# List tenants (should return empty array initially)
curl http://localhost:8080/api/v1/tenants

# Check API docs
open http://localhost:8080/swagger-ui.html
```

### 3. Create Test Tenant

**For Kubernetes:**
```bash
curl -X POST http://localhost:8080/api/v1/tenants \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "test-tenant",
    "organizationName": "Test Org"
  }'

# Verify namespace created
kubectl get namespace | grep airflow-test-tenant
```

**For ECS:**
```bash
curl -X POST http://localhost:8080/api/v1/tenants \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "test-tenant",
    "organizationName": "Test Org"
  }'

# Verify cluster created
aws ecs list-clusters | grep test-tenant
```

**For EC2:**
```bash
curl -X POST http://localhost:8080/api/v1/tenants \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "test-tenant",
    "organizationName": "Test Org"
  }'

# Verify EC2 instance created
aws ec2 describe-instances --filters "Name=tag:tenant-id,Values=test-tenant"
```

### 4. Create Test Deployment

```bash
curl -X POST http://localhost:8080/api/v1/deployments \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "test-tenant",
    "deploymentId": "test-deployment",
    "airflowVersion": "2.7.0",
    "executorType": "LOCAL",
    "schedulerCpu": "1024",
    "schedulerMemory": "2048",
    "webserverCpu": "512",
    "webserverMemory": "1024"
  }'
```

**Verify Deployment:**

- **Kubernetes:** `kubectl get pods -n airflow-test-tenant`
- **ECS:** `aws ecs list-tasks --cluster managed-airflow-test-tenant`
- **EC2:** `ssh -i key.pem ec2-user@<IP> 'docker ps'`

## Troubleshooting

### Common Issues

#### 1. Control Plane Won't Start

**Check logs:**
```bash
# If running locally
tail -f logs/application.log

# If running in Kubernetes
kubectl logs -n airflow-control-plane -l app=managed-airflow-platform

# If running on EC2
ssh -i key.pem ec2-user@<IP> 'tail -f app.log'
```

**Common causes:**
- Database connection issues
- Invalid AWS credentials (for ECS/EC2)
- Missing permissions
- Invalid configuration

#### 2. Kubernetes: Tenant Creation Fails

```bash
# Check control plane logs
kubectl logs -n airflow-control-plane -l app=managed-airflow-platform | grep ERROR

# Check RBAC permissions
kubectl auth can-i create namespace --as=system:serviceaccount:airflow-control-plane:airflow-sa

# Verify RBAC configuration
kubectl get clusterrolebinding | grep airflow
```

#### 3. ECS: Cluster Creation Fails

```bash
# Check AWS credentials
aws sts get-caller-identity

# Check IAM permissions
aws ecs describe-clusters --clusters test-cluster

# Check control plane logs
tail -f logs/application.log | grep ECS
```

**Common causes:**
- Insufficient IAM permissions
- Region mismatch
- VPC/subnet not found
- EFS not accessible

#### 4. EC2: Instance Not Starting

```bash
# Check EC2 instances
aws ec2 describe-instances --filters "Name=tag:managed-by,Values=airflow-control-plane"

# Check security groups
aws ec2 describe-security-groups --group-ids sg-xxx

# Check instance status
aws ec2 describe-instance-status --instance-ids i-xxx
```

**Common causes:**
- Invalid AMI ID
- Instance type not available in region
- No available IPs in subnet
- IAM instance profile issues

#### 5. Deployment Creation Fails

**Kubernetes:**
```bash
# Check Helm releases
helm list -n airflow-test-tenant

# Check Helm status
helm status <release-name> -n airflow-test-tenant

# Check pod events
kubectl describe pod -n airflow-test-tenant <pod-name>
```

**ECS:**
```bash
# Check service status
aws ecs describe-services --cluster managed-airflow-test-tenant --services test-deployment-scheduler

# Check task failures
aws ecs describe-tasks --cluster managed-airflow-test-tenant --tasks <task-id>

# Check CloudWatch logs
aws logs tail /ecs/managed-airflow/test-deployment/scheduler --follow
```

**EC2:**
```bash
# SSH to instance
ssh -i key.pem ec2-user@<IP>

# Check Docker Compose
docker-compose ps

# Check logs
docker-compose logs

# Check SSM command status
aws ssm list-commands --filters "Key=ExecutionStage,Values=Failed"
```

### Debug Mode

Enable debug logging in `application.yml`:

```yaml
logging:
  level:
    com.airflow.platform: DEBUG
    io.kubernetes: DEBUG  # For Kubernetes
    software.amazon.awssdk: DEBUG  # For AWS
```

### Network Connectivity Issues

**Test AWS connectivity:**
```bash
# Test ECS API
aws ecs list-clusters

# Test EC2 API
aws ec2 describe-instances

# Test EFS access (from EC2/ECS)
mount -t efs fs-xxxxxxxx:/ /mnt/efs
```

**Test Kubernetes connectivity:**
```bash
# Test cluster access
kubectl cluster-info

# Test from control plane pod
kubectl run -it --rm debug --image=curlimages/curl --restart=Never -- \
  curl http://kubernetes.default.svc.cluster.local
```

### Cleanup

**Kubernetes:**
```bash
# Delete all tenant namespaces
kubectl delete namespace -l managed-by=airflow-control-plane

# Delete control plane
kubectl delete -f kubernetes/control-plane-deployment.yaml
kubectl delete namespace airflow-control-plane

# Delete KEDA
helm uninstall keda -n keda
kubectl delete namespace keda
```

**ECS:**
```bash
# Delete via API (recommended)
curl -X DELETE http://localhost:8080/api/v1/tenants/{tenantId}

# Or manually
aws ecs delete-cluster --cluster managed-airflow-{tenant-id}

# Destroy infrastructure
cd infrastructure/ecs/terraform
terraform destroy
```

**EC2:**
```bash
# Delete via API (recommended)
curl -X DELETE http://localhost:8080/api/v1/tenants/{tenantId}

# Or manually
aws ec2 terminate-instances --instance-ids i-xxx

# Destroy infrastructure
cd infrastructure/ec2/terraform
terraform destroy
```

## Next Steps

- Review the [User Guide](USER_GUIDE.md) for platform usage
- Review architecture documentation:
  - [Kubernetes Architecture](ARCHITECTURE.md)
  - [ECS Architecture](ARCHITECTURE_ECS.md)
  - [EC2 Architecture](ARCHITECTURE_EC2.md)
- Set up monitoring and alerting
- Configure automated backups
- Implement CI/CD pipelines for control plane updates

## Support

For issues and questions:
- **Logs:** Check component logs for errors
- **Documentation:** Review architecture docs
- **API Docs:** Use Swagger UI for API reference
- **Community:** Join Apache Airflow community forums

## Additional Resources

- [Apache Airflow Documentation](https://airflow.apache.org/docs/)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [AWS ECS Documentation](https://docs.aws.amazon.com/ecs/)
- [AWS EC2 Documentation](https://docs.aws.amazon.com/ec2/)
- [Terraform Documentation](https://www.terraform.io/docs/)

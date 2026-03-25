# Managed Airflow Platform

A production-ready, multi-cloud, multi-tenant platform for deploying and managing Apache Airflow instances across **Local, AWS EC2, AWS ECS, and Kubernetes**, similar to Astronomer.

## Overview

The Managed Airflow Platform provides a complete solution for organizations to deploy, manage, and scale Apache Airflow across multiple cloud providers and deployment targets. Built with Java Spring Boot and React, it offers a powerful control plane for managing Airflow deployments with features like auto-scaling, multi-tenancy, and comprehensive monitoring.

### 🎯 Multiple Deployment Options

Choose the deployment option that fits your needs:

| Option | Best For | Monthly Cost | Complexity | Auto-Scaling | HA |
|--------|----------|--------------|------------|--------------|-----|
| **Local** | Learning, Dev, Testing | Free | Simplest | No | No |
| **AWS EC2** | Dev/Test, PoC | ~$35/tenant | Easy | Manual | Low |
| **AWS ECS** | Test/Staging, Small Prod | ~$137/tenant | Medium | Yes | Medium |
| **Kubernetes** | Production, Enterprise | ~$150/tenant | Complex | Yes | High |

📖 **[Complete Deployment Options Comparison](docs/DEPLOYMENT_OPTIONS.md)**

## Key Features

### Deployment Flexibility
- **Multiple Deployment Options** - Choose from Local, EC2+Docker, AWS ECS Fargate, or Kubernetes based on your needs
- **Multi-Cloud Support** - Deploy on AWS (EKS/ECS/EC2), Google GKE, Azure AKS, on-premises, or locally
- **Provider Abstraction** - Switch between deployment providers with configuration change

### Multi-Tenancy & Isolation
- **Tenant Isolation** - Dedicated environments for each tenant
  - Local: Directory per tenant
  - Kubernetes: Namespace per tenant
  - ECS: Cluster per tenant
  - EC2: Instance per tenant
- **Resource Management** - Configurable CPU and memory allocations per component
- **Independent Scaling** - Each tenant scales independently

### Auto-Scaling
- **Kubernetes**: KEDA-based auto-scaling on queue depth
- **ECS**: AWS Application Auto Scaling on CPU/memory metrics
- **EC2/Local**: Manual scaling via Docker Compose

### Management & Monitoring
- **Control Plane UI** - React-based web interface for managing tenants and deployments
- **DAG Management** - Web-based DAG creation with code editor, Git integration, and deployment
- **REST API** - Complete API for programmatic management
- **Multiple Executor Support** - Local, Celery, Kubernetes, and hybrid executors
- **Monitoring Ready** - Built-in health checks and metrics endpoints

### Cost Optimization
- **Containerized Databases** - PostgreSQL and Redis run as containers (ECS/EC2)
- **Flexible Sizing** - Right-size resources per workload
- **Spot Instances** - Support for cost-effective compute (where applicable)

## Architecture

### High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                           Users / Operators                                   │
└──────────────────────────────┬───────────────────────────────────────────────┘
                               │
┌──────────────────────────────▼───────────────────────────────────────────────┐
│                       Control Plane UI (React)                                │
│     • Tenant Management  • Deployment Management  • Monitoring                │
└──────────────────────────────┬───────────────────────────────────────────────┘
                               │
┌──────────────────────────────▼───────────────────────────────────────────────┐
│                 Control Plane API (Spring Boot)                               │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐                    │
│  │ Provider │  │ Provider │  │ Provider │  │ Provider │                    │
│  │  Local   │  │  K8s     │  │  ECS     │  │  EC2     │                    │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘                    │
└───────┼─────────────┼─────────────┼─────────────┼────────────────────────────┘
        │             │             │             │
┌───────▼───────┐ ┌──▼─────────┐ ┌─▼──────────┐ ┌▼────────────┐
│   Local       │ │ Kubernetes │ │  AWS ECS   │ │  AWS EC2    │
│ ┌───────────┐ │ │ ┌────────┐ │ │ ┌────────┐ │ │ ┌─────────┐ │
│ │ Directory │ │ │ │Namespace│ │ │ │Cluster │ │ │ │Instance │ │
│ │           │ │ │ │        │ │ │ │        │ │ │ │         │ │
│ │• Postgres │ │ │ │• Sched.│ │ │ │• Post. │ │ │ │• Post.  │ │
│ │• Redis    │ │ │ │• Web   │ │ │ │• Redis │ │ │ │• Redis  │ │
│ │• Sched.   │ │ │ │• Work. │ │ │ │• Sched.│ │ │ │• Sched. │ │
│ │• Web      │ │ │ │• Post. │ │ │ │• Web   │ │ │ │• Web    │ │
│ │• Workers  │ │ │ │• Redis │ │ │ │• Work. │ │ │ │• Work.  │ │
│ └───────────┘ │ └────────┘ │ └────────┘ │ └─────────┘ │
│ • Docker      │ │ • KEDA     │ │ • EFS      │ │ • SSM       │
│ • Ports 8080+ │ │ • Helm     │ │ • AutoScale│ │ • Docker    │
└───────────────┘ └────────────┘ └────────────┘ └─────────────┘
```

### Architecture Documentation

- 📓 **[Local Testing Guide](docs/LOCAL_TESTING.md)** - Complete guide for local deployment and testing
- 📘 **[Kubernetes Architecture](docs/ARCHITECTURE.md)** - Detailed Kubernetes deployment architecture
- 📗 **[ECS Architecture](docs/ARCHITECTURE_ECS.md)** - AWS ECS Fargate deployment architecture
- 📙 **[EC2 Architecture](docs/ARCHITECTURE_EC2.md)** - AWS EC2 Docker Compose deployment architecture

## Quick Start

### Choose Your Path

Pick the deployment option that matches your needs:

#### 💻 Option 0: Local (Fastest, Free)

**Best for**: Learning, development, feature testing, demos

```bash
# 1. Run setup script
cd infrastructure/local
./setup-local.sh

# 2. Start control plane
cd ../../control-plane
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 3. Create tenant and deployment via API
curl -X POST http://localhost:8080/api/v1/tenants ...
```

📖 **[Complete Local Testing Guide](docs/LOCAL_TESTING.md)**

#### 🚀 Option 1: EC2 (Simple, Low Cost)

**Best for**: Development, testing, proof of concepts

```bash
# 1. Setup infrastructure
cd infrastructure/ec2/terraform
terraform init
terraform apply

# 2. Start control plane
cd control-plane
mvn spring-boot:run -Dspring-boot.run.profiles=ec2

# 3. Create tenant and deployment via API
curl -X POST http://localhost:8080/api/v1/tenants ...
```

📖 **[Complete EC2 Setup Guide](docs/SETUP.md#option-3-aws-ec2-setup)**

#### ☁️ Option 2: ECS (Managed Containers)

**Best for**: Staging, small-medium production, cost-conscious deployments

```bash
# 1. Setup infrastructure
cd infrastructure/ecs/terraform
terraform init
terraform apply

# 2. Start control plane
cd control-plane
mvn spring-boot:run -Dspring-boot.run.profiles=ecs

# 3. Create tenant and deployment via API
curl -X POST http://localhost:8080/api/v1/tenants ...
```

📖 **[Complete ECS Setup Guide](docs/SETUP.md#option-2-aws-ecs-setup)**

#### ⎈ Option 3: Kubernetes (Enterprise Production)

**Best for**: Large-scale production, multi-cloud, enterprise deployments

```bash
# 1. Setup Kubernetes cluster (Minikube, EKS, GKE, AKS)
minikube start --cpus=4 --memory=8192

# 2. Install KEDA
helm repo add kedacore https://kedacore.github.io/charts
helm install keda kedacore/keda --namespace keda --create-namespace

# 3. Add Airflow Helm repo
helm repo add apache-airflow https://airflow.apache.org

# 4. Start control plane
cd control-plane
mvn spring-boot:run -Dspring-boot.run.profiles=kubernetes

# 5. Create tenant and deployment via UI or API
```

📖 **[Complete Kubernetes Setup Guide](docs/SETUP.md#option-1-kubernetes-setup)**

### Prerequisites

**Common Requirements:**
- Java 17+
- Maven 3.8+
- Git

**Local-Specific:**
- Docker 20.10+
- Docker Compose 2.0+
- 8GB+ RAM (4GB allocated to Docker recommended)

**Kubernetes-Specific:**
- kubectl
- Helm 3.x
- Kubernetes cluster access

**AWS-Specific (ECS & EC2):**
- AWS CLI v2
- AWS account with appropriate permissions
- Terraform 1.0+

📋 **[Complete Prerequisites List](docs/SETUP.md#prerequisites)**

## Usage

### Creating a Tenant

Each tenant gets isolated resources (namespace/cluster/instance depending on deployment option).

**Via UI:**
1. Navigate to Tenants page
2. Click "Create Tenant"
3. Fill in details and submit

**Via API:**
```bash
curl -X POST http://localhost:8080/api/v1/tenants \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "data-team",
    "organizationName": "Data Engineering Team"
  }'
```

### Creating an Airflow Deployment

**Via UI:**
1. Navigate to Deployments page
2. Click "Create Deployment"
3. Select tenant and configure Airflow settings
4. Submit to deploy

**Via API:**
```bash
curl -X POST http://localhost:8080/api/v1/deployments \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "data-team",
    "deploymentId": "prod-etl",
    "name": "Production ETL",
    "airflowVersion": "3.1.8",
    "executorType": "CELERY",
    "minWorkers": 1,
    "maxWorkers": 5,
    "schedulerCpu": "1024",
    "schedulerMemory": "2048",
    "webserverCpu": "512",
    "webserverMemory": "1024",
    "workerCpu": "1024",
    "workerMemory": "2048"
  }'
```

**Deployment Time:**
- EC2: ~3-5 minutes
- ECS: ~5-7 minutes
- Kubernetes: ~5-10 minutes

### Creating and Managing DAGs

**Via UI:**
1. Navigate to DAGs page
2. Click "Create DAG"
3. Select deployment, enter DAG details
4. Write Python code in Monaco editor
5. Save and deploy to Airflow

**Via API:**
```bash
curl -X POST http://localhost:8080/api/v1/dags \
  -H "Content-Type: application/json" \
  -d '{
    "deploymentId": "prod-etl",
    "name": "My ETL Pipeline",
    "description": "Daily ETL job",
    "fileName": "my_etl_dag.py",
    "dagCode": "from airflow import DAG\n# DAG code here...",
    "gitRepository": "https://github.com/user/airflow-dags.git",
    "gitBranch": "main",
    "owner": "data-team",
    "tags": "etl,daily,production"
  }'
```

**Features:**
- Monaco code editor with Python syntax highlighting
- Basic DAG validation
- Git repository integration (optional)
- Deploy directly to Airflow instances
- View, edit, and manage all DAGs from UI

### Accessing Airflow

**After deployment completes:**

1. Via UI: Click "Open" button on the deployment
2. Via API: Get the `webserverUrl` from deployment details

**Default Credentials:**
- Username: `admin`
- Password: Varies by deployment (see [User Guide](docs/USER_GUIDE.md#accessing-airflow))

📖 **[Complete User Guide](docs/USER_GUIDE.md)**

## Project Structure

```
managed-airflow-platform/
├── control-plane/                     # Spring Boot backend
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/airflow/platform/
│   │   │   │       ├── config/             # Configuration classes
│   │   │   │       ├── controller/         # REST controllers
│   │   │   │       │   ├── TenantController.java
│   │   │   │       │   ├── DeploymentController.java
│   │   │   │       │   └── DagController.java
│   │   │   │       ├── service/            # Business logic
│   │   │   │       │   ├── TenantService.java
│   │   │   │       │   ├── AirflowDeploymentService.java
│   │   │   │       │   └── DagService.java
│   │   │   │       ├── model/              # JPA entities
│   │   │   │       │   ├── Tenant.java
│   │   │   │       │   ├── AirflowDeployment.java
│   │   │   │       │   └── Dag.java
│   │   │   │       ├── repository/         # Data access
│   │   │   │       │   ├── TenantRepository.java
│   │   │   │       │   ├── AirflowDeploymentRepository.java
│   │   │   │       │   └── DagRepository.java
│   │   │   │       ├── dto/                # Request/Response DTOs
│   │   │   │       │   ├── DagCreateRequest.java
│   │   │   │       │   ├── DagUpdateRequest.java
│   │   │   │       │   └── DagResponse.java
│   │   │   │       ├── exception/          # Exception handling
│   │   │   │       ├── provider/           # Deployment providers
│   │   │   │       │   ├── CloudProvider.java
│   │   │   │       │   ├── DeploymentProvider.java
│   │   │   │       │   └── impl/
│   │   │   │       │       ├── LocalCloudProvider.java
│   │   │   │       │       ├── LocalDeploymentProvider.java
│   │   │   │       │       ├── KubernetesCloudProvider.java
│   │   │   │       │       ├── HelmDeploymentProvider.java
│   │   │   │       │       ├── ECSCloudProvider.java
│   │   │   │       │       ├── ECSDeploymentProvider.java
│   │   │   │       │       ├── EC2CloudProvider.java
│   │   │   │       │       └── EC2DeploymentProvider.java
│   │   │   │       └── util/               # Utilities
│   │   │   └── resources/
│   │   │       └── application.yml         # Configuration
│   │   └── test/
│   └── pom.xml
│
├── frontend/                          # React frontend
│   ├── src/
│   │   ├── components/               # Reusable components
│   │   │   ├── Sidebar.js
│   │   │   └── Header.js
│   │   ├── pages/                    # Page components
│   │   │   ├── Dashboard.js
│   │   │   ├── Tenants.js
│   │   │   ├── Deployments.js
│   │   │   ├── DeploymentDetails.js
│   │   │   ├── Dags.js              # DAG listing page
│   │   │   ├── DagForm.js           # DAG create/edit page
│   │   │   └── DagDetails.js        # DAG details page
│   │   ├── services/                 # API client
│   │   │   └── api.js               # REST API client (includes dagAPI)
│   │   └── utils/                    # Utilities
│   ├── public/
│   └── package.json
│
├── infrastructure/                    # Infrastructure as Code
│   ├── local/                        # Local deployment
│   │   ├── setup-local.sh            # Prerequisites check script
│   │   └── README.md                 # Local setup guide
│   │
│   ├── ecs/                          # ECS deployment
│   │   ├── terraform/                # Terraform configs
│   │   │   ├── main.tf
│   │   │   ├── variables.tf
│   │   │   └── outputs.tf
│   │   ├── ECS_DOCKER_UPDATE.md     # ECS Docker guide
│   │   └── README.md
│   │
│   ├── ec2/                          # EC2 deployment
│   │   ├── terraform/                # Terraform configs
│   │   │   ├── main.tf
│   │   │   ├── variables.tf
│   │   │   └── outputs.tf
│   │   └── README.md
│   │
│   └── kubernetes/                   # K8s-specific infrastructure
│       └── terraform/                # Terraform for EKS/GKE/AKS
│
├── helm-charts/                      # Helm charts (for K8s)
│   ├── airflow-deployment/          # Airflow deployment chart
│   │   ├── Chart.yaml
│   │   ├── values.yaml
│   │   └── templates/
│   └── platform-infrastructure/     # Platform infrastructure
│
├── kubernetes/                       # K8s manifests
│   ├── namespace/                   # Namespace definitions
│   ├── rbac/                        # RBAC configurations
│   ├── ingress/                     # Ingress configurations
│   └── monitoring/                  # Monitoring setup
│
├── docs/                            # Documentation
│   ├── LOCAL_TESTING.md            # Local testing guide
│   ├── ARCHITECTURE.md             # Kubernetes architecture
│   ├── ARCHITECTURE_ECS.md         # ECS architecture
│   ├── ARCHITECTURE_EC2.md         # EC2 architecture
│   ├── SETUP.md                    # Setup guide (all options)
│   └── USER_GUIDE.md               # User guide (all options)
│
├── scripts/                         # Utility scripts
└── README.md                        # This file
```

## Technology Stack

### Backend (Control Plane)
- **Java 17** - Programming language
- **Spring Boot 3.2** - Application framework
- **Spring Data JPA** - Data access
- **Spring Security** - Security framework
- **Kubernetes Java Client** - K8s integration
- **AWS SDK v2** - ECS and EC2 integration
- **PostgreSQL** - Database (production)
- **H2** - Database (development)
- **Maven** - Build tool

### Frontend
- **React 18** - UI framework
- **React Router** - Routing
- **Ant Design** - UI component library
- **Monaco Editor** - Code editor for DAG creation
- **Axios** - HTTP client
- **Recharts** - Data visualization

### Infrastructure & Deployment

**Local:**
- **Docker 20.10+** - Containerization
- **Docker Compose 2.0+** - Multi-container orchestration
- **Local filesystem** - Directory-based tenant isolation

**Kubernetes:**
- **Kubernetes 1.24+** - Container orchestration
- **Helm 3.x** - Package manager
- **KEDA** - Event-driven autoscaling
- **Apache Airflow Helm Chart** - Official Airflow deployment

**AWS ECS:**
- **AWS ECS Fargate** - Serverless containers
- **AWS EFS** - Persistent storage for PostgreSQL
- **AWS Application Auto Scaling** - Auto-scaling
- **AWS Systems Manager** - Secrets management

**AWS EC2:**
- **AWS EC2** - Virtual machines
- **Docker** - Containerization
- **Docker Compose** - Multi-container orchestration
- **AWS Systems Manager** - Remote management (SSH-free)

**Common:**
- **Apache Airflow 2.7+** - Workflow orchestration
- **PostgreSQL 13** - Metadata database
- **Redis 7** - Celery message broker
- **Terraform** - Infrastructure as Code

## API Documentation

Once the control plane is running, access the interactive API documentation:

- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI Spec**: `http://localhost:8080/v3/api-docs`

### Key Endpoints

**Tenants:**
- `POST /api/v1/tenants` - Create tenant
- `GET /api/v1/tenants` - List tenants
- `GET /api/v1/tenants/{tenantId}` - Get tenant
- `DELETE /api/v1/tenants/{tenantId}` - Delete tenant

**Deployments:**
- `POST /api/v1/deployments` - Create Airflow deployment
- `GET /api/v1/deployments` - List deployments
- `GET /api/v1/deployments/{deploymentId}` - Get deployment
- `PUT /api/v1/deployments/{deploymentId}` - Update deployment
- `DELETE /api/v1/deployments/{deploymentId}` - Delete deployment

**DAGs:**
- `POST /api/v1/dags` - Create DAG
- `GET /api/v1/dags` - List all DAGs
- `GET /api/v1/dags/{dagId}` - Get DAG details
- `GET /api/v1/dags/deployment/{deploymentId}` - List DAGs by deployment
- `PUT /api/v1/dags/{dagId}` - Update DAG
- `DELETE /api/v1/dags/{dagId}` - Delete DAG
- `POST /api/v1/dags/{dagId}/deploy` - Deploy DAG to Airflow

## Configuration

### Application Profiles

The control plane supports three profiles for different deployment targets:

```yaml
# For Kubernetes deployments
spring.profiles.active=kubernetes

# For ECS deployments
spring.profiles.active=ecs

# For EC2 deployments
spring.profiles.active=ec2
```

### Kubernetes Configuration

```yaml
spring:
  profiles:
    active: kubernetes

deployment:
  provider: kubernetes

helm:
  chart:
    path: ../helm-charts/airflow-deployment
  repo:
    name: apache-airflow
    url: https://airflow.apache.org
```

### ECS Configuration

```yaml
spring:
  profiles:
    active: ecs

deployment:
  provider: ecs

aws:
  region: us-east-1
  ecs:
    cluster-prefix: managed-airflow
    task-execution-role-arn: arn:aws:iam::ACCOUNT:role/...
    task-role-arn: arn:aws:iam::ACCOUNT:role/...
  efs:
    file-system-id: fs-xxxxxxxxx
    access-point-id: fsap-xxxxxxxxx
  vpc:
    subnet-ids:
      - subnet-xxxxxxxx
      - subnet-yyyyyyyy
    security-group-ids:
      - sg-xxxxxxxxx
```

### EC2 Configuration

```yaml
spring:
  profiles:
    active: ec2

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
```

📖 **[Complete Configuration Guide](docs/SETUP.md#configuration)**

## Deployment to Production

### Option 1: Deploy Control Plane to Kubernetes

```bash
# 1. Build Docker images
cd control-plane
mvn clean package -DskipTests
docker build -t your-registry/managed-airflow-control-plane:latest .
docker push your-registry/managed-airflow-control-plane:latest

# 2. Deploy to Kubernetes
kubectl apply -f kubernetes/namespace/control-plane-namespace.yaml
kubectl apply -f kubernetes/rbac/control-plane-rbac.yaml
kubectl apply -f kubernetes/control-plane-deployment.yaml
```

### Option 2: Deploy Control Plane to EC2

```bash
# 1. Build JAR
cd control-plane
mvn clean package -DskipTests

# 2. Copy to EC2 and run
scp -i key.pem target/*.jar ec2-user@<IP>:/home/ec2-user/
ssh -i key.pem ec2-user@<IP>
nohup java -jar managed-airflow-control-plane-*.jar --spring.profiles.active=ecs > app.log 2>&1 &
```

### Option 3: Deploy Control Plane to ECS

```bash
# 1. Build and push Docker image
docker build -t your-registry/managed-airflow-control-plane:latest .
docker push your-registry/managed-airflow-control-plane:latest

# 2. Create ECS task definition and service
# (Use Terraform or AWS Console)
```

📖 **[Complete Deployment Guide](docs/SETUP.md#control-plane-deployment)**

## Monitoring

### Health Checks

```bash
# Control plane health
curl http://localhost:8080/actuator/health

# Metrics
curl http://localhost:8080/actuator/metrics

# Prometheus metrics
curl http://localhost:8080/actuator/prometheus
```

### Deployment-Specific Monitoring

**Kubernetes:**
```bash
# Pod logs
kubectl logs -n airflow-{tenant-id} -l component=scheduler

# Resource usage
kubectl top pods -n airflow-{tenant-id}
```

**ECS:**
```bash
# CloudWatch logs
aws logs tail /ecs/managed-airflow/{deployment-id}/scheduler --follow

# ECS metrics
aws cloudwatch get-metric-statistics --namespace AWS/ECS ...
```

**EC2:**
```bash
# SSH to instance and check Docker logs
ssh -i key.pem ec2-user@<IP>
docker-compose logs -f

# Or use SSM (SSH-free)
aws ssm start-session --target <instance-id>
```

### Grafana Dashboards

Import pre-built dashboards for:
- Platform overview
- Kubernetes/ECS/EC2 metrics
- Airflow task metrics
- Auto-scaling metrics

## Testing

### Backend Tests

```bash
cd control-plane
mvn test
```

### Frontend Tests

```bash
cd frontend
npm test
```

### Integration Tests

```bash
# Deploy to test cluster
cd infrastructure/ecs/terraform  # or ec2, or kubernetes
terraform apply

# Create test tenant and deployment
./scripts/run-integration-tests.sh
```

## Troubleshooting

### Common Issues by Platform

#### Kubernetes
- **Pod won't start**: Check resource limits and node capacity
- **KEDA not scaling**: Verify ScaledObject configuration
- **RBAC errors**: Check service account permissions

#### ECS
- **Task fails to start**: Check EFS mount and task execution role
- **Auto-scaling not working**: Verify Application Auto Scaling policies
- **Network issues**: Check security groups allow required ports

#### EC2
- **Docker Compose fails**: Check Docker installation and permissions
- **SSM not working**: Verify IAM instance profile
- **Out of resources**: Choose larger instance type

📖 **[Complete Troubleshooting Guide](docs/USER_GUIDE.md#monitoring-and-troubleshooting)**

## Security Considerations

### Production Recommendations

1. **Authentication & Authorization**
   - Implement JWT or OAuth2 for control plane
   - Enable Airflow RBAC
   - Integrate with enterprise SSO

2. **Network Security**
   - Use private subnets where possible
   - Enable VPC Flow Logs
   - Implement Network Policies (Kubernetes)
   - Restrict security group rules

3. **Secret Management**
   - Use AWS Secrets Manager / Kubernetes Secrets
   - Enable encryption at rest and in transit
   - Rotate credentials regularly
   - Never commit secrets to Git

4. **Compliance**
   - Enable audit logging
   - Implement pod security standards (Kubernetes)
   - Use encrypted EBS/EFS volumes
   - Regular security scans

## Cost Optimization

### Cost Breakdown (per tenant/month)

**EC2 (t3.medium):**
- Instance: ~$30
- EBS storage: ~$5
- **Total: ~$35/month**

**ECS (Optimized):**
- Fargate tasks: ~$130
- EFS storage: ~$3
- Data transfer: ~$1
- CloudWatch logs: ~$3
- **Total: ~$137/month**

**Kubernetes (EKS):**
- Control plane: ~$73/month
- Worker nodes: ~$60/month (shared)
- EBS storage: ~$10/month
- Load balancer: ~$18/month (shared)
- **Total: ~$150/month per tenant**
  *(Cost decreases per tenant with more tenants on same cluster)*

### Cost Optimization Tips

1. **Use Spot Instances** (where applicable)
2. **Right-size resources** based on actual usage
3. **Enable EFS lifecycle policies** (transition to IA storage)
4. **Use Fargate Spot** for non-critical workloads (70% savings)
5. **Stop EC2 instances** when not in use
6. **Share infrastructure** (ALB, EFS) across tenants where possible

## Roadmap

### ✅ Completed Features

- [x] Multiple deployment options (Kubernetes, ECS, EC2, Local)
- [x] Provider abstraction for multi-cloud support
- [x] Docker Compose-based deployment
- [x] AWS ECS with Fargate support
- [x] Auto-scaling across all platforms
- [x] Multi-tenant architecture
- [x] REST API and Web UI
- [x] Comprehensive documentation
- [x] **DAG Management UI** - Create, edit, and deploy DAGs from web interface
- [x] **Code Editor** - Monaco editor with Python syntax highlighting
- [x] **DAG Validation** - Basic validation for DAG code

### 🚧 In Progress

- [ ] Enhanced monitoring dashboards
- [ ] Cost tracking and billing
- [ ] Advanced DAG validation (Python AST parsing)

### 📋 Planned Features

- [ ] **Git-sync Integration** - Automatic DAG synchronization from Git repositories
- [ ] **DAG Deployment Pipeline** - Automated deployment of DAGs to Airflow instances
- [ ] **DAG Testing Framework** - Pre-deployment testing and validation
- [ ] Multi-cluster support
- [ ] Self-service tenant registration
- [ ] Plugin marketplace
- [ ] Compliance reporting (SOC 2, GDPR)
- [ ] CI/CD integration for DAGs
- [ ] Advanced authentication (SSO, MFA)
- [ ] Resource quota management
- [ ] Additional cloud providers (GCP Native, Azure Native)
- [ ] Backup and restore automation
- [ ] Migration tools (between deployment options)
- [ ] DAG version history and rollback

## Documentation

### 📚 Complete Documentation

- **[Setup Guide](docs/SETUP.md)** - Setup instructions for all deployment options
- **[User Guide](docs/USER_GUIDE.md)** - Complete usage guide
- **[Kubernetes Architecture](docs/ARCHITECTURE.md)** - Kubernetes deployment details
- **[ECS Architecture](docs/ARCHITECTURE_ECS.md)** - ECS deployment details
- **[EC2 Architecture](docs/ARCHITECTURE_EC2.md)** - EC2 deployment details

### 🔧 Infrastructure Guides

- **[Local Infrastructure Setup](infrastructure/local/README.md)** - Local development setup guide
- **[ECS Infrastructure Setup](infrastructure/ecs/README.md)** - ECS Terraform guide
- **[EC2 Infrastructure Setup](infrastructure/ec2/README.md)** - EC2 Terraform guide
- **[ECS Docker Update](infrastructure/ecs/ECS_DOCKER_UPDATE.md)** - ECS containerization guide

## Contributing

Contributions are welcome! Please follow these guidelines:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Development Guidelines

- Follow Java code conventions
- Write unit tests for new features
- Update documentation as needed
- Test deployment changes in a test environment
- Run `mvn test` before submitting PR

## License

This project is licensed under the Apache License 2.0 - see the LICENSE file for details.

## Support

- **Documentation**: See `docs/` directory
- **Issues**: Open an issue on GitHub
- **Discussions**: Join our community discussions
- **Email**: support@example.com

## Acknowledgments

- [Apache Airflow](https://airflow.apache.org/) - The amazing workflow orchestration platform
- [Astronomer](https://www.astronomer.io/) - Inspiration for managed Airflow
- [KEDA](https://keda.sh/) - Kubernetes event-driven autoscaling
- [Spring Boot](https://spring.io/projects/spring-boot) - Application framework
- [React](https://reactjs.org/) - UI framework
- [Docker](https://www.docker.com/) - Containerization
- [Terraform](https://www.terraform.io/) - Infrastructure as Code

## Related Projects

- [Apache Airflow](https://github.com/apache/airflow)
- [Official Airflow Helm Chart](https://github.com/apache/airflow/tree/main/chart)
- [KEDA](https://github.com/kedacore/keda)
- [Docker Compose](https://docs.docker.com/compose/)
- [AWS ECS](https://aws.amazon.com/ecs/)
- [AWS Systems Manager](https://aws.amazon.com/systems-manager/)

## Authors

- Your Name - Initial work

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for version history.

---

**Built with ❤️ for the data engineering community**

*Making Apache Airflow deployment simple, scalable, and cost-effective across any infrastructure.*

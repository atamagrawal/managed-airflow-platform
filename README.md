# Managed Airflow Platform

A production-ready, multi-cloud, multi-tenant platform for deploying and managing Apache Airflow instances across **Local, AWS EC2, AWS ECS, and Kubernetes**.

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

📖 **[Complete Deployment Options Comparison](DEPLOYMENT_OPTIONS.md)**

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
- **Project Management** - Structured Airflow projects with `dags/`, `plugins/`, `include/`, `tests/`, and related metadata
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

# 3. Create tenant and deployment via API (JWT required — see Usage / Authentication)
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

# 3. Create tenant and deployment via API (JWT required — see Usage / Authentication)
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

# 3. Create tenant and deployment via API (JWT required — see Usage / Authentication)
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

# 4. Start control plane (Kubernetes/Helm provider is the default when `deployment.provider` is not overridden)
cd control-plane
mvn spring-boot:run

# 5. Create tenant and deployment via UI or API
```

📖 **[Complete Kubernetes Setup Guide](docs/SETUP.md#option-1-kubernetes-setup)**

### Prerequisites

**Common Requirements:**
- Java 21 (matches `control-plane/pom.xml`; Temurin 21 recommended)
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
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | jq -r '.accessToken')

curl -X POST http://localhost:8080/api/v1/tenants \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "name": "Data Engineering",
    "email": "data-eng@example.com",
    "organization": "Data Engineering",
    "cloudProvider": "AWS",
    "clusterName": "local",
    "region": "local"
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
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "tenantId": "<tenantId-from-create-tenant-response>",
    "name": "Production ETL",
    "description": "Main production stack",
    "airflowVersion": "3.2.0",
    "executorType": "CELERY",
    "minWorkers": 1,
    "maxWorkers": 5,
    "schedulerCpu": "1000m",
    "schedulerMemory": "2Gi",
    "webserverCpu": "500m",
    "webserverMemory": "1Gi",
    "workerCpu": "1000m",
    "workerMemory": "2Gi"
  }'
```

**Deployment Time:**
- EC2: ~3-5 minutes
- ECS: ~5-7 minutes
- Kubernetes: ~5-10 minutes

### Creating and managing projects

**Via UI:**
1. Navigate to Projects page
2. Click "Create Project"
3. Enter project details and configuration
4. Add DAGs, plugins, and other files
5. Deploy entire project structure to Airflow

**Via API:**
```bash
curl -X POST http://localhost:8080/api/v1/projects \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "name": "my-data-project",
    "description": "Production data pipelines",
    "airflowVersion": "3.2.0",
    "requirementsTxt": "pandas==2.0.0\nrequests==2.31.0",
    "packagesTxt": "gcc\nlibpq-dev",
    "owner": "data-team",
    "tags": "production,etl"
  }'
```

**Project Structure:**
- `dags/` - Airflow DAG files
- `plugins/` - Custom Airflow plugins
- `include/` - Shared utilities and libraries
- `tests/` - Unit tests for DAGs
- `requirements.txt` - Python dependencies
- `packages.txt` - OS-level packages
- `Dockerfile` - Custom Docker configuration
- `airflow_settings.yaml` - Airflow connections and variables
- `.airflowignore` - Files to ignore
- `.env` - Environment variables

**Features:**
- Complete, opinionated project layout for Airflow code and dependencies
- Manage multiple DAGs within a project
- Shared dependencies and utilities
- Custom Docker images
- Git repository integration
- Deploy entire project as a unit

### DAGs inside projects

DAGs are normal Python files in a project (for example under `dags/`). Create or edit them from the **Project browser** and **project editor**, then **Deploy** the project to an Airflow deployment.

**Trigger runs:** From a deployed project, use **Trigger DAGs** in the UI, or call the project trigger API (see **Projects** endpoints below). The control plane calls Airflow’s REST API using the `dag_id` parsed from each DAG file.

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
│   │   │   │       │   ├── AuthController.java
│   │   │   │       │   ├── TenantController.java
│   │   │   │       │   ├── DeploymentController.java
│   │   │   │       │   ├── ProjectController.java
│   │   │   │       │   ├── AiChatController.java
│   │   │   │       │   └── …
│   │   │   │       ├── service/            # Business logic
│   │   │   │       │   ├── TenantService.java
│   │   │   │       │   ├── AirflowDeploymentService.java
│   │   │   │       │   └── ProjectService.java
│   │   │   │       ├── model/              # JPA entities
│   │   │   │       │   ├── Tenant.java
│   │   │   │       │   ├── AirflowDeployment.java
│   │   │   │       │   ├── Project.java
│   │   │   │       │   └── ProjectFile.java
│   │   │   │       ├── repository/         # Data access
│   │   │   │       │   ├── TenantRepository.java
│   │   │   │       │   ├── AirflowDeploymentRepository.java
│   │   │   │       │   ├── ProjectRepository.java
│   │   │   │       │   └── ProjectFileRepository.java
│   │   │   │       ├── dto/                # Request/Response DTOs
│   │   │   │       │   ├── ProjectCreateRequest.java
│   │   │   │       │   ├── ProjectUpdateRequest.java
│   │   │   │       │   ├── ProjectResponse.java
│   │   │   │       │   └── ProjectFileRequest.java
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
│   │   │   ├── Projects.js          # Project listing page
│   │   │   ├── ProjectDetails.js    # Project details page
│   │   │   └── ProjectCodeEditor.js # Project file editor
│   │   ├── components/               # Reusable components
│   │   │   ├── ProjectForm.js       # Project create/edit form
│   │   │   └── ...
│   │   ├── services/                 # API client
│   │   │   └── api.js               # REST API client (tenantAPI, deploymentAPI, projectAPI)
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
│   ├── USER_GUIDE.md               # User guide (all options)
│   ├── PROJECTS.md                 # Project layout and API
│   ├── PROJECT_EDITOR_AI_ASSISTANT.md
│   └── DAG_DEPLOYMENT_STRATEGIES.md
│
├── scripts/                         # Utility scripts
└── README.md                        # This file
```

## Technology Stack

### Backend (Control Plane)
- **Java 21** - Programming language
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
- **CodeMirror 6** (`@uiw/react-codemirror`) - Code editor for DAGs and project files
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
- **Apache Airflow 3.2.0** - Workflow orchestration (only versions listed in `SupportedAirflowVersions` are accepted)
- **PostgreSQL 15** - Metadata database (Docker Compose `docker-compose.yml` profile `prod`)
- **Redis 7** - Celery message broker
- **Terraform** - Infrastructure as Code

## API Documentation

Once the control plane is running, access the interactive API documentation:

- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI Spec**: `http://localhost:8080/v3/api-docs`

### Authentication

All `/api/v1/**` routes except `POST /api/v1/auth/login` and `GET /api/v1/public/**` require a JWT: `Authorization: Bearer <accessToken>` from the login response. **Tenant** APIs (`/api/v1/tenants/**`) and **admin** APIs (`/api/v1/admin/**`) require role `ADMIN`. Non-admin users are scoped to a home tenant from `platform.security.users` in `application.yml`.

### Key endpoints

**Auth:**
- `POST /api/v1/auth/login` — JWT (`accessToken`, `tokenType`, `expiresInMs`, roles, `tenantScope`)
- `GET /api/v1/auth/me` — current user from token

**Tenants (ADMIN):**
- `POST /api/v1/tenants` — Create tenant (server generates `tenantId` from `name`)
- `GET /api/v1/tenants` — List tenants
- `GET /api/v1/tenants/{tenantId}` — Get tenant
- `DELETE /api/v1/tenants/{tenantId}` — Delete tenant

**Deployments:**
- `GET /api/v1/deployments/config` — Provider id + local idle-timeout hint
- `POST /api/v1/deployments` — Create deployment (`deploymentId` is generated from `name`, not supplied in the body)
- `GET /api/v1/deployments` — List deployments visible to the caller
- `GET /api/v1/deployments/{deploymentId}` — Get deployment
- `GET /api/v1/deployments/tenant/{tenantId}` — List by tenant (caller must be allowed to see that tenant)
- `PUT /api/v1/deployments/{deploymentId}` — Update deployment (adjust `minWorkers` / `maxWorkers` here; there is no separate scale URL)
- `DELETE /api/v1/deployments/{deploymentId}` — Delete deployment
- `POST /api/v1/deployments/{deploymentId}/local-stack/start|stop|keep-alive` — Local provider: Docker lifecycle

**Projects:**
- `POST /api/v1/projects` — Create project (optional `tenantId` for admins)
- `GET /api/v1/projects` — List projects
- `GET /api/v1/projects/{projectId}` — Get project
- `GET /api/v1/projects/deployment/{deploymentId}` — List projects linked to a deployment
- `PUT /api/v1/projects/{projectId}` — Update project metadata
- `DELETE /api/v1/projects/{projectId}` — Delete project
- `POST /api/v1/projects/{projectId}/deployments/{deploymentId}` — Link project to deployment
- `DELETE /api/v1/projects/{projectId}/deployments/{deploymentId}` — Unlink
- `POST /api/v1/projects/{projectId}/deploy?deploymentId=` — Deploy files to Airflow
- `POST /api/v1/projects/{projectId}/trigger?deploymentId=` — Trigger DAG runs (optional `fileName`)
- `POST|GET /api/v1/projects/{projectId}/files` — Add or list files
- `PUT /api/v1/projects/{projectId}/files/{fileId}` — Update file content

**DAG insights & Airflow UI handoff:**
- `GET /api/v1/dag-insights/...`, `POST /api/v1/dag-insights/sync` — Cached DAG runs / debug / import errors
- `GET /api/v1/deployed-dags` — Deployed DAG index
- `POST /api/v1/deployments/{deploymentId}/airflow-ui-handoff` — Short-lived browser handoff into Airflow

**AI assistant (project editor):**
- `POST /api/v1/ai/chat`, `GET /api/v1/ai/status`

**Connections sync:** `POST /api/v1/environment/connections/sync`

See Swagger UI for full schemas.

## Configuration

### Application profiles and `deployment.provider`

Spring profiles are defined in `control-plane/src/main/resources/application.yml`:

| Profile / mode | Purpose |
|----------------|---------|
| *(none)* | Defaults: in-memory H2 control-plane DB, `deployment.provider` defaults to **kubernetes** (`HelmDeploymentProvider`) |
| `local` | Local Docker Compose Airflow under `~/airflow-deployments` |
| `ecs` | AWS ECS Fargate |
| `ec2` | AWS EC2 + Docker Compose |
| `prod` | PostgreSQL for the control plane (compose uses this with Postgres) |

```bash
# Examples
export SPRING_PROFILES_ACTIVE=local
export SPRING_PROFILES_ACTIVE=ecs,prod   # combine when you wire Postgres + ECS
```

Kubernetes mode does not require a dedicated `application-kubernetes.yml`; omit `local`/`ecs`/`ec2` (or set `deployment.provider=kubernetes`) to use the Helm provider.

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
    subnet-id: subnet-xxxxxxxxx
    security-group-id: sg-xxxxxxxxx
    iam-instance-profile: EC2AirflowInstanceProfile
    command-timeout: 300
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

Prometheus scrape endpoint is not exposed by default; `management.endpoints.web.exposure.include` lists `health`, `info`, and `metrics` only. Add `prometheus` there if you enable the Prometheus registry in Spring Boot.
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
   - The control plane already issues JWTs (`/api/v1/auth/login`); change `platform.security.jwt-secret`, passwords, and user entries for production
   - Enable Airflow RBAC and strong Fernet / metadata DB secrets in each deployment
   - Plan SSO or an identity provider in front of the UI/API if required

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
- [x] **Project Management** - Structured multi-file Airflow projects with full CRUD operations
- [x] **Project File Management** - Manage DAGs, plugins, includes, tests within projects
- [x] **DAG Management UI** - Create, edit, and deploy DAGs from web interface
- [x] **Code Editor** - Monaco editor with Python syntax highlighting
- [x] **DAG Validation** - Basic validation for DAG code
- [x] **DAG Run Trigger** - Trigger DAG runs directly from the UI
- [x] **JWT login** - Control plane authentication and tenant-scoped non-admin users
- [x] **Flow Deck AI assistant** - Server-mediated LLM chat for the project editor (`/api/v1/ai/*`)
- [x] **DAG insights cache** - Synced DAG run and import-error views from each deployment’s Airflow API

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
- **[Projects](docs/PROJECTS.md)** - Project structure and REST usage
- **[Project editor AI](docs/PROJECT_EDITOR_AI_ASSISTANT.md)** - Flow Deck AI panel and server config
- **[DAG deployment strategies](docs/DAG_DEPLOYMENT_STRATEGIES.md)** - `dag.deployment.strategy` options
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

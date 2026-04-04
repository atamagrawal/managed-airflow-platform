# Managed Airflow Platform - Architecture Documentation

## Overview

The Managed Airflow Platform is a multi-cloud, multi-tenant solution for deploying and managing Apache Airflow instances. This document describes the architecture, components, and design decisions of the platform.

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         Users / Admins                          │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Control Plane UI                           │
│                     (React Application)                         │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Control Plane API                            │
│              (Spring Boot REST API)                             │
│  ┌──────────────┬──────────────┬───────────────────────────┐  │
│  │   Tenant     │  Deployment  │    Kubernetes             │  │
│  │  Management  │  Management  │    Management             │  │
│  └──────────────┴──────────────┴───────────────────────────┘  │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Kubernetes Cluster(s)                         │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              Control Plane Namespace                     │  │
│  │  - Control Plane Pods                                    │  │
│  │  - PostgreSQL Database                                   │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │            Tenant Namespace (airflow-tenant-1)           │  │
│  │  ┌────────────────────────────────────────────────────┐  │  │
│  │  │          Airflow Deployment                        │  │  │
│  │  │  - Airflow Webserver                               │  │  │
│  │  │  - Airflow Scheduler                               │  │  │
│  │  │  - Airflow Workers (Auto-scaled by KEDA)           │  │  │
│  │  │  - PostgreSQL (Metadata DB)                        │  │  │
│  │  │  - Redis (Celery Backend)                          │  │  │
│  │  └────────────────────────────────────────────────────┘  │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │            Tenant Namespace (airflow-tenant-2)           │  │
│  │  └─── Similar Airflow Deployment Structure              │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                  KEDA (Auto-scaling)                     │  │
│  │                  Ingress Controller                      │  │
│  │                  Monitoring Stack                        │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## Core Components

### 1. Control Plane

The control plane is the management layer responsible for orchestrating all tenant and deployment operations.

#### 1.1 Control Plane API (Spring Boot)

**Location:** `control-plane/`

**Responsibilities:**
- Tenant lifecycle management (create, read, delete)
- Airflow deployment lifecycle management (create, update, delete)
- Kubernetes resource orchestration
- Helm chart deployment management
- REST API for UI and external integrations

**Key Components:**
- **Controllers:** REST endpoints for tenants, deployments, and projects
- **Services:** Business logic for tenant, deployment, and project operations
- **Repositories:** Data access layer using Spring Data JPA
- **Models:** JPA entities (Tenant, AirflowDeployment, Project, ProjectFile, …)
- **Kubernetes Service:** Integration with Kubernetes API
- **Helm Service:** Helm chart deployment via shell commands
- **Project Service:** Project lifecycle, file storage, deploy to Airflow, DAG run triggers

**Technology Stack:**
- Java 17
- Spring Boot 3.2
- Spring Data JPA
- Kubernetes Java Client
- PostgreSQL / H2 (development)

#### 1.2 Control Plane UI (React)

**Location:** `frontend/`

**Responsibilities:**
- User interface for platform administration
- Tenant management dashboard
- Deployment creation and monitoring
- Real-time status visualization

**Key Features:**
- Dashboard with platform statistics
- Tenant management (CRUD operations)
- Deployment management (create, view, delete)
- Deployment details view
- **DAG management (create, edit, view, deploy)**
- **Monaco code editor for DAG development**
- Responsive design using Ant Design

**Technology Stack:**
- React 18
- React Router
- Ant Design (UI components)
- Monaco Editor (code editor)
- Axios (HTTP client)
- Recharts (data visualization)

### 2. Multi-Tenancy Model

The platform uses **namespace-based isolation** for multi-tenancy:

- Each tenant gets a dedicated Kubernetes namespace (e.g., `airflow-tenant-1`)
- Each namespace contains isolated Airflow deployments
- Resource quotas can be applied per namespace
- Network policies can enforce isolation
- RBAC controls access to resources

**Tenant Entity:**
```java
Tenant {
  - tenantId: Unique identifier
  - name: Display name
  - email: Contact email
  - organization: Organization name
  - status: PENDING/ACTIVE/SUSPENDED/DELETED
  - kubernetesNamespace: Associated K8s namespace
  - cloudProvider: AWS/GCP/AZURE
  - clusterName: Target cluster
  - region: Cloud region
}
```

### 3. Airflow Deployment Architecture

Each Airflow deployment consists of the following components:

#### 3.1 Core Airflow Components

1. **Webserver**
   - User interface for Airflow
   - Exposed via Kubernetes Service/Ingress
   - Configurable resources (CPU, memory)

2. **Scheduler**
   - Orchestrates task execution
   - Monitors DAG files
   - Schedules tasks to workers

3. **Workers**
   - Execute tasks
   - Auto-scaled based on queue depth (KEDA)
   - Support multiple executor types

4. **Metadata Database (PostgreSQL)**
   - Stores Airflow metadata
   - Task state, DAG definitions, connections
   - Can be shared or dedicated per tenant

5. **Message Broker (Redis)**
   - Required for CeleryExecutor
   - Task queue management
   - Worker coordination

#### 3.2 Executor Types

The platform supports multiple executor types:

1. **LocalExecutor**
   - Tasks run in scheduler process
   - Simple, low resource usage
   - Not suitable for production

2. **CeleryExecutor**
   - Distributed task execution
   - Worker autoscaling support
   - Best for production workloads

3. **KubernetesExecutor**
   - Each task runs in separate pod
   - Dynamic resource allocation
   - High isolation

4. **CeleryKubernetesExecutor**
   - Hybrid approach
   - Flexibility for different task types

### 4. Auto-Scaling with KEDA

**KEDA (Kubernetes Event Driven Autoscaling)** enables worker autoscaling based on queue depth.

**Location:** `helm-charts/airflow-deployment/templates/keda-scaledobject.yaml`

**How it works:**
1. KEDA monitors the Celery queue length (or task queue in PostgreSQL)
2. When queue depth increases, KEDA scales up workers
3. When queue depth decreases, KEDA scales down workers
4. Min/max replica counts are configurable per deployment

**ScaledObject Configuration:**
```yaml
minReplicaCount: 1
maxReplicaCount: 5
pollingInterval: 5s
cooldownPeriod: 30s
triggers:
  - type: postgresql
    query: "SELECT COUNT(*) FROM task_instance WHERE state='queued'"
```

### 5. Helm Chart Management

**Location:** `helm-charts/airflow-deployment/`

The platform uses the official Apache Airflow Helm chart with custom configurations:

**Chart.yaml:**
- Defines chart metadata
- Specifies dependencies (official Airflow chart)

**values.yaml:**
- Default configuration values
- Resource specifications
- KEDA configuration
- Ingress settings

**Deployment Process:**
1. Control plane receives deployment request
2. Validates tenant and configuration
3. Generates Helm values based on request
4. Executes `helm install` with custom values
5. Monitors deployment status
6. Updates deployment entity in database

### 6. Kubernetes Integration

**Location:** `control-plane/src/main/java/com/airflow/platform/service/KubernetesService.java`

The Kubernetes service handles:
- Namespace creation/deletion
- Secret management
- Resource quota enforcement
- RBAC configuration
- Health checks

**Authentication:**
- Uses in-cluster config when running inside K8s
- Uses kubeconfig for local development

**RBAC:**
- Control plane has ClusterRole with permissions to:
  - Manage namespaces
  - Create/delete resources in tenant namespaces
  - Manage KEDA ScaledObjects
  - Configure ingress

### 7. Project-Based DAGs

DAG definitions live as **project files** (`ProjectFile` with type `DAG`, typically under `dags/` paths). There is no separate first-class `Dag` entity or `/api/v1/dags` control-plane API.

**Deploy:** `ProjectService` writes the project tree (including DAG `.py` files) to the deployment filesystem according to `dag.deployment.strategy` (`DagDeploymentConfig`).

**Trigger:** The UI calls `POST /api/v1/projects/{projectId}/trigger` with a deployment id (and optionally a single DAG file name). The backend parses each selected DAG file’s Python content to obtain the Airflow `dag_id`, then calls the Airflow REST API (`/api/v1/dags/.../dagRuns` or `/api/v2/dags/.../dagRuns` depending on Airflow version).

**Frontend:** Users author DAGs in the project browser / project code editor; **Trigger DAGs** actions live on project and deployed-project screens.

### 8. Data Model

#### 8.1 Entity Relationships

```
Tenant (1) ──────< (N) AirflowDeployment
Project (1) ──────< (N) ProjectFile
(Projects link to deployments via project–deployment association; see `ProjectService` / controllers.)
```

#### 8.2 Database Schema

**Tenants Table:**
```sql
tenants (
  id BIGINT PRIMARY KEY,
  tenant_id VARCHAR(100) UNIQUE,
  name VARCHAR(200),
  email VARCHAR(200) UNIQUE,
  organization VARCHAR(500),
  status VARCHAR(50),
  kubernetes_namespace VARCHAR(100),
  cloud_provider VARCHAR(50),
  cluster_name VARCHAR(100),
  region VARCHAR(50),
  created_at TIMESTAMP,
  updated_at TIMESTAMP
)
```

**Airflow Deployments Table:**
```sql
airflow_deployments (
  id BIGINT PRIMARY KEY,
  deployment_id VARCHAR(100) UNIQUE,
  tenant_id BIGINT FOREIGN KEY,
  name VARCHAR(200),
  description VARCHAR(500),
  airflow_version VARCHAR(50),
  executor_type VARCHAR(50),
  status VARCHAR(50),
  namespace VARCHAR(100),
  helm_release_name VARCHAR(200),
  min_workers INT,
  max_workers INT,
  scheduler_cpu VARCHAR,
  scheduler_memory VARCHAR,
  worker_cpu VARCHAR,
  worker_memory VARCHAR,
  webserver_cpu VARCHAR,
  webserver_memory VARCHAR,
  webserver_url VARCHAR(500),
  ingress_host VARCHAR(500),
  custom_config TEXT,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  deployed_at TIMESTAMP
)
```

## Network Architecture

### Ingress Configuration

**Location:** `kubernetes/ingress/`

Each Airflow deployment can have:
- Optional custom ingress hostname
- TLS/SSL termination
- Path-based or host-based routing

**Example:**
```
https://tenant1-airflow.example.com → Tenant 1 Webserver
https://tenant2-airflow.example.com → Tenant 2 Webserver
```

### Service Mesh (Optional)

For advanced deployments:
- Istio or Linkerd for service-to-service encryption
- Traffic management and observability
- Circuit breaking and retries

## Security Architecture

### 1. Authentication & Authorization

**Current State (MVP):**
- Basic authentication disabled for API
- Ready for JWT/OAuth2 integration

**Production Recommendations:**
- Implement JWT-based authentication
- Integrate with OAuth2 providers (Okta, Auth0, Keycloak)
- Role-Based Access Control (RBAC)
- Multi-factor authentication (MFA)

### 2. Network Security

- Namespace isolation via NetworkPolicies
- Pod Security Policies/Standards
- Ingress with TLS termination
- Private container registries

### 3. Secret Management

- Kubernetes Secrets for sensitive data
- Integration with external secret managers (HashiCorp Vault, AWS Secrets Manager)
- Encryption at rest

### 4. Airflow Security

- Webserver authentication (LDAP, OAuth)
- DAG code security scanning
- Connection encryption
- Audit logging

## Monitoring & Observability

### 1. Metrics

**Prometheus Integration:**
- Control plane metrics via Spring Actuator
- Airflow metrics via StatsD exporter
- KEDA metrics for autoscaling
- Kubernetes cluster metrics

**Key Metrics:**
- Deployment status and health
- Task execution rates
- Worker utilization
- Queue depth
- Resource consumption

### 2. Logging

**Centralized Logging:**
- ELK Stack (Elasticsearch, Logstash, Kibana)
- Loki + Grafana
- Cloud-native solutions (CloudWatch, Stackdriver)

**Log Sources:**
- Control plane application logs
- Airflow component logs (scheduler, webserver, workers)
- Kubernetes events
- Audit logs

### 3. Alerting

**Alert Channels:**
- Email
- Slack/Teams
- PagerDuty
- Custom webhooks

**Alert Types:**
- Deployment failures
- Resource exhaustion
- Scheduler downtime
- Task failures exceeding threshold

### 4. Dashboards

**Grafana Dashboards:**
- Platform overview (tenants, deployments)
- Per-tenant resource usage
- Airflow task execution metrics
- Kubernetes cluster health

## Scalability Considerations

### 1. Control Plane Scaling

- Horizontal scaling: Multiple control plane replicas
- Load balancer in front of API
- Database connection pooling
- Async operations for long-running tasks

### 2. Airflow Deployment Scaling

- Worker autoscaling via KEDA
- Scheduler can run in HA mode (multiple replicas)
- Database read replicas for high query loads

### 3. Multi-Cluster Support

For large-scale deployments:
- Deploy control plane in management cluster
- Manage Airflow deployments across multiple clusters
- Use Kubernetes federation or custom multi-cluster controllers

## Disaster Recovery

### 1. Backup Strategy

- Control plane database backups (automated)
- Airflow metadata database backups per tenant
- DAG code version control (Git)
- Kubernetes resource manifests backup

### 2. Recovery Procedures

- Restore control plane from backup
- Redeploy Airflow instances using stored configurations
- Restore metadata databases
- Re-sync DAG repositories

## Multi-Cloud Support

The platform is designed to work across cloud providers:

### AWS
- EKS for Kubernetes
- RDS for PostgreSQL
- S3 for logs and DAGs
- ALB for ingress

### GCP
- GKE for Kubernetes
- Cloud SQL for PostgreSQL
- GCS for logs and DAGs
- Cloud Load Balancing

### Azure
- AKS for Kubernetes
- Azure Database for PostgreSQL
- Azure Blob Storage
- Application Gateway

### On-Premises
- Self-managed Kubernetes (kubeadm, Rancher)
- Self-hosted PostgreSQL
- NFS/Ceph for storage
- NGINX/HAProxy for ingress

## Future Enhancements

1. **Advanced DAG Management**
   - Git-sync automation for DAG deployment
   - DAG version control and rollback
   - CI/CD integration for DAG testing
   - Advanced Python validation (AST parsing)
   - DAG testing framework
   - DAG marketplace with templates

2. **Cost Management**
   - Resource usage tracking per tenant
   - Cost allocation and billing
   - Budget alerts

3. **Advanced Autoscaling**
   - Predictive autoscaling based on historical patterns
   - Custom scaling metrics

4. **Self-Service Portal**
   - Tenant self-registration
   - Self-service deployment upgrades
   - Usage analytics dashboard

5. **Marketplace**
   - Pre-built DAG templates
   - Plugins and operators marketplace
   - Integration marketplace

6. **Compliance**
   - SOC 2 compliance
   - GDPR compliance
   - Audit trail and reporting

## Conclusion

The Managed Airflow Platform provides a robust, scalable, and multi-tenant solution for deploying Apache Airflow across multiple cloud providers. The architecture is designed for flexibility, security, and ease of management, making it suitable for organizations of all sizes.

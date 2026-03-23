# Managed Airflow Platform - User Guide

This guide explains how to use the Managed Airflow Platform to create and manage Apache Airflow deployments.

## Table of Contents

1. [Getting Started](#getting-started)
2. [Managing Tenants](#managing-tenants)
3. [Managing Deployments](#managing-deployments)
4. [Accessing Airflow](#accessing-airflow)
5. [Monitoring and Troubleshooting](#monitoring-and-troubleshooting)
6. [API Reference](#api-reference)
7. [Best Practices](#best-practices)

## Getting Started

### Accessing the Platform

1. Navigate to the platform URL (e.g., `https://airflow-platform.example.com`)
2. You'll see the dashboard with platform statistics

### Dashboard Overview

The dashboard displays:
- **Total Tenants** - Number of registered tenants
- **Total Deployments** - Number of Airflow deployments
- **Running Deployments** - Currently running deployments
- **Failed Deployments** - Deployments that failed

## Managing Tenants

Tenants are organizations or teams that use the platform. Each tenant can have multiple Airflow deployments.

### Creating a Tenant

1. Click **Tenants** in the sidebar
2. Click the **Create Tenant** button
3. Fill in the tenant details:
   - **Name** - Display name for the tenant (e.g., "Data Engineering Team")
   - **Email** - Contact email address
   - **Organization** - Organization name (optional)
   - **Cloud Provider** - Select AWS, GCP, or Azure
   - **Cluster Name** - Kubernetes cluster name (optional)
   - **Region** - Cloud region (e.g., "us-east-1")
4. Click **OK** to create the tenant

**What happens:**
- A unique tenant ID is generated (e.g., "data-engineering-team")
- A dedicated Kubernetes namespace is created (e.g., "airflow-data-engineering-team")
- The tenant status is set to ACTIVE

### Viewing Tenants

The Tenants page displays:
- **Tenant ID** - Unique identifier
- **Name** - Display name
- **Email** - Contact email
- **Organization** - Organization name
- **Cloud Provider** - AWS/GCP/Azure
- **Status** - PENDING/ACTIVE/SUSPENDED/DELETED
- **Created At** - Creation timestamp

### Deleting a Tenant

1. Click the **Delete** button next to the tenant
2. Confirm the deletion
3. The tenant and all its deployments will be deleted

**Warning:** This action cannot be undone. All Airflow deployments for this tenant will be deleted.

## Managing Deployments

Deployments are Apache Airflow instances running in Kubernetes.

### Creating a Deployment

1. Click **Deployments** in the sidebar
2. Click the **Create Deployment** button
3. Fill in the deployment details:

#### Basic Information
- **Tenant** - Select the tenant (required)
- **Deployment Name** - Name for this deployment (e.g., "Production ETL")
- **Description** - Brief description (optional)

#### Airflow Configuration
- **Airflow Version** - Helm chart version (e.g., "1.13.0")
- **Executor Type** - Choose the executor:
  - **Local Executor** - Simple, runs tasks in scheduler process
  - **Celery Executor** - Distributed, recommended for production
  - **Kubernetes Executor** - Each task runs in separate pod
  - **Celery Kubernetes Executor** - Hybrid approach

#### Worker Autoscaling
- **Min Workers** - Minimum number of workers (default: 1)
- **Max Workers** - Maximum number of workers (default: 5)

Workers will automatically scale between min and max based on task queue depth.

#### Network (Optional)
- **Ingress Host** - Custom domain for Airflow UI (e.g., "airflow.example.com")

4. Click **OK** to create the deployment

**What happens:**
- A unique deployment ID is generated
- Helm installs Airflow in the tenant's namespace
- Components are deployed:
  - Airflow Webserver
  - Airflow Scheduler
  - Airflow Workers
  - PostgreSQL database
  - Redis (for Celery executor)
- KEDA is configured for worker autoscaling
- Deployment status changes: PENDING → DEPLOYING → RUNNING

**Note:** Deployment can take 5-10 minutes depending on cluster resources.

### Viewing Deployments

The Deployments page displays:
- **Deployment ID** - Unique identifier
- **Name** - Display name
- **Tenant ID** - Owner tenant
- **Airflow Version** - Helm chart version
- **Executor** - Executor type
- **Status** - Deployment status
- **Workers** - Min-Max worker count
- **Created At** - Creation timestamp

### Deployment Status

| Status | Description |
|--------|-------------|
| PENDING | Deployment created, waiting to start |
| DEPLOYING | Helm is installing Airflow components |
| RUNNING | Deployment is healthy and running |
| UPDATING | Configuration is being updated |
| FAILED | Deployment failed, check logs |
| STOPPED | Deployment is stopped |
| DELETED | Deployment has been removed |

### Updating a Deployment

Currently, deployments can be updated via the API (UI support coming soon).

Example API call to update worker configuration:

```bash
curl -X PUT https://airflow-platform.example.com/api/v1/deployments/<deployment-id> \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "data-engineering-team",
    "name": "Production ETL",
    "description": "Updated description",
    "airflowVersion": "1.13.0",
    "executorType": "CELERY",
    "minWorkers": 2,
    "maxWorkers": 10,
    "schedulerCpu": "1000m",
    "schedulerMemory": "2Gi",
    "workerCpu": "1000m",
    "workerMemory": "2Gi",
    "webserverCpu": "500m",
    "webserverMemory": "1Gi"
  }'
```

### Deleting a Deployment

1. Click the **Delete** button next to the deployment
2. Confirm the deletion
3. The deployment will be removed from Kubernetes

**Warning:** This will delete all Airflow components and metadata. DAGs and task history will be lost unless backed up.

### Viewing Deployment Details

1. Click on a deployment in the list (or deployment ID)
2. View detailed information:
   - Basic information
   - Resource configuration
   - Network configuration
   - Timestamps

## Accessing Airflow

### Accessing Airflow Webserver

Once a deployment is RUNNING:

1. Go to the Deployments page
2. Find your deployment
3. Click the **Open** button in the Actions column
4. The Airflow UI will open in a new tab

**Default Credentials (Official Airflow Helm Chart):**
- Username: `admin`
- Password: Check the Airflow documentation or Kubernetes secret

**To get the password:**

```bash
# Get namespace
NAMESPACE=$(kubectl get deployment -A | grep <deployment-id> | awk '{print $1}')

# Get admin password
kubectl get secret -n $NAMESPACE \
  <helm-release-name>-webserver-secret \
  -o jsonpath='{.data.webserver-secret-key}' | base64 -d
```

### Accessing via kubectl

```bash
# List all Airflow deployments
kubectl get pods -A | grep airflow

# Port-forward to webserver
kubectl port-forward -n <namespace> svc/<release-name>-webserver 8080:8080

# Access at http://localhost:8080
```

### Custom Domain (Ingress)

If you specified an ingress host during deployment:

1. Ensure DNS points to your cluster's ingress controller
2. Access Airflow at `https://<ingress-host>`

Example: `https://prod-airflow.example.com`

## Monitoring and Troubleshooting

### Checking Deployment Status

**Via UI:**
- Navigate to Deployments page
- Check the Status column

**Via API:**

```bash
curl https://airflow-platform.example.com/api/v1/deployments/<deployment-id>
```

### Common Issues

#### 1. Deployment Stuck in DEPLOYING

**Possible causes:**
- Insufficient cluster resources
- Image pull errors
- Persistent volume provisioning issues

**Troubleshooting:**

```bash
# Get namespace
NAMESPACE=airflow-<tenant-id>

# Check pod status
kubectl get pods -n $NAMESPACE

# Check pod events
kubectl describe pod -n $NAMESPACE <pod-name>

# Check pod logs
kubectl logs -n $NAMESPACE <pod-name>
```

#### 2. Workers Not Autoscaling

**Possible causes:**
- KEDA not installed
- ScaledObject misconfigured
- No tasks in queue

**Troubleshooting:**

```bash
# Check KEDA installation
kubectl get pods -n keda

# Check ScaledObject
kubectl get scaledobject -n $NAMESPACE
kubectl describe scaledobject -n $NAMESPACE <name>

# Check worker pods
kubectl get pods -n $NAMESPACE | grep worker
```

#### 3. Cannot Access Airflow UI

**Possible causes:**
- Service not ready
- Ingress misconfigured
- Network policy blocking access

**Troubleshooting:**

```bash
# Check webserver service
kubectl get svc -n $NAMESPACE | grep webserver

# Check webserver logs
kubectl logs -n $NAMESPACE -l component=webserver

# Port-forward to test
kubectl port-forward -n $NAMESPACE svc/<release>-webserver 8080:8080
```

### Viewing Logs

**Control Plane Logs:**

```bash
kubectl logs -n airflow-control-plane -l app=managed-airflow-platform --tail=100 -f
```

**Airflow Component Logs:**

```bash
# Scheduler logs
kubectl logs -n $NAMESPACE -l component=scheduler --tail=100

# Webserver logs
kubectl logs -n $NAMESPACE -l component=webserver --tail=100

# Worker logs
kubectl logs -n $NAMESPACE -l component=worker --tail=100
```

### Resource Usage

Check resource consumption:

```bash
# Pod resource usage
kubectl top pods -n $NAMESPACE

# Node resource usage
kubectl top nodes
```

## API Reference

The platform exposes a REST API for programmatic access.

### Base URL

```
https://airflow-platform.example.com/api/v1
```

### API Documentation

Swagger UI: `https://airflow-platform.example.com/swagger-ui.html`

OpenAPI Spec: `https://airflow-platform.example.com/v3/api-docs`

### Authentication

Currently, authentication is disabled for MVP. Production deployments should implement JWT or OAuth2.

### Endpoints

#### Tenants

**List all tenants:**
```bash
GET /api/v1/tenants
```

**Get tenant by ID:**
```bash
GET /api/v1/tenants/{tenantId}
```

**Create tenant:**
```bash
POST /api/v1/tenants
Content-Type: application/json

{
  "name": "Engineering Team",
  "email": "eng@example.com",
  "organization": "Acme Corp",
  "cloudProvider": "AWS",
  "clusterName": "prod-cluster",
  "region": "us-east-1"
}
```

**Delete tenant:**
```bash
DELETE /api/v1/tenants/{tenantId}
```

#### Deployments

**List all deployments:**
```bash
GET /api/v1/deployments
```

**Get deployment by ID:**
```bash
GET /api/v1/deployments/{deploymentId}
```

**Get deployments by tenant:**
```bash
GET /api/v1/deployments/tenant/{tenantId}
```

**Create deployment:**
```bash
POST /api/v1/deployments
Content-Type: application/json

{
  "tenantId": "engineering-team",
  "name": "Production ETL",
  "description": "Main ETL pipeline",
  "airflowVersion": "1.13.0",
  "executorType": "CELERY",
  "minWorkers": 1,
  "maxWorkers": 5,
  "schedulerCpu": "1000m",
  "schedulerMemory": "2Gi",
  "workerCpu": "1000m",
  "workerMemory": "2Gi",
  "webserverCpu": "500m",
  "webserverMemory": "1Gi",
  "ingressHost": "airflow.example.com"
}
```

**Update deployment:**
```bash
PUT /api/v1/deployments/{deploymentId}
Content-Type: application/json

{
  "tenantId": "engineering-team",
  "name": "Production ETL",
  "description": "Updated description",
  "airflowVersion": "1.13.0",
  "executorType": "CELERY",
  "minWorkers": 2,
  "maxWorkers": 10,
  ...
}
```

**Delete deployment:**
```bash
DELETE /api/v1/deployments/{deploymentId}
```

### Example: Using curl

```bash
# Create tenant
TENANT_RESPONSE=$(curl -X POST https://airflow-platform.example.com/api/v1/tenants \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Data Team",
    "email": "data@example.com",
    "cloudProvider": "AWS",
    "region": "us-east-1"
  }')

TENANT_ID=$(echo $TENANT_RESPONSE | jq -r '.tenantId')

# Create deployment
curl -X POST https://airflow-platform.example.com/api/v1/deployments \
  -H "Content-Type: application/json" \
  -d "{
    \"tenantId\": \"$TENANT_ID\",
    \"name\": \"My Airflow\",
    \"airflowVersion\": \"1.13.0\",
    \"executorType\": \"CELERY\",
    \"minWorkers\": 1,
    \"maxWorkers\": 5
  }"
```

## Best Practices

### 1. Tenant Organization

- Create one tenant per team or environment
- Use descriptive tenant names
- Keep email addresses up to date

### 2. Deployment Sizing

**Small workloads (< 100 tasks/day):**
```
Executor: Local or Celery
Min Workers: 1
Max Workers: 2
Scheduler: 500m CPU, 1Gi memory
Worker: 500m CPU, 1Gi memory
```

**Medium workloads (100-1000 tasks/day):**
```
Executor: Celery
Min Workers: 2
Max Workers: 5
Scheduler: 1000m CPU, 2Gi memory
Worker: 1000m CPU, 2Gi memory
```

**Large workloads (> 1000 tasks/day):**
```
Executor: Celery or CeleryKubernetes
Min Workers: 3
Max Workers: 10+
Scheduler: 2000m CPU, 4Gi memory
Worker: 2000m CPU, 4Gi memory
```

### 3. Autoscaling Configuration

- Set `minWorkers` to handle baseline load
- Set `maxWorkers` based on cluster capacity
- Monitor queue depth and adjust as needed
- Consider cost vs. performance tradeoffs

### 4. Naming Conventions

**Tenants:**
- Use lowercase with hyphens
- Example: "data-engineering", "ml-team", "analytics"

**Deployments:**
- Include environment and purpose
- Examples: "prod-etl", "staging-ml-pipeline", "dev-analytics"

### 5. Resource Management

- Right-size resources based on workload
- Monitor resource usage via Kubernetes metrics
- Use resource quotas for cost control
- Clean up unused deployments

### 6. Security

- Use custom ingress hosts with TLS
- Rotate Airflow credentials regularly
- Use Kubernetes secrets for sensitive data
- Implement network policies for isolation
- Enable Airflow authentication (LDAP, OAuth)

### 7. High Availability

For critical deployments:
- Run multiple scheduler replicas (Airflow 2.x)
- Use external PostgreSQL (managed service)
- Use external Redis (managed service)
- Configure pod disruption budgets
- Enable persistent volumes

### 8. Backup and Recovery

- Backup Airflow metadata database regularly
- Version control DAGs in Git
- Document deployment configurations
- Test recovery procedures

### 9. Monitoring

- Set up alerts for deployment failures
- Monitor task success rates
- Track resource utilization
- Monitor worker autoscaling behavior

### 10. Upgrades

- Test upgrades in non-production first
- Review Airflow upgrade notes
- Back up before upgrading
- Use blue-green deployment strategy

## Troubleshooting Guide

### Deployment Creation Fails

1. Check control plane logs
2. Verify tenant exists and is ACTIVE
3. Check cluster has sufficient resources
4. Verify Helm can access chart repository

### Workers Not Starting

1. Check worker pod status: `kubectl get pods -n <namespace>`
2. Check worker pod logs: `kubectl logs -n <namespace> <worker-pod>`
3. Verify executor configuration
4. Check Redis connectivity (for Celery)

### Tasks Not Running

1. Check scheduler logs
2. Verify DAGs are loaded
3. Check database connectivity
4. Verify worker connectivity
5. Check task queue (Redis)

### Performance Issues

1. Monitor resource usage
2. Check for task bottlenecks
3. Increase worker count
4. Optimize DAG code
5. Consider executor type change

## Getting Help

- **Documentation:** Review docs in `docs/` directory
- **Logs:** Check component logs for errors
- **API Docs:** Use Swagger UI for API reference
- **Community:** Join Airflow Slack/community forums
- **Support:** Contact platform administrators

## Next Steps

- Explore Airflow documentation: https://airflow.apache.org/docs/
- Learn about DAG development
- Set up monitoring and alerting
- Integrate with your CI/CD pipeline
- Configure authentication and authorization

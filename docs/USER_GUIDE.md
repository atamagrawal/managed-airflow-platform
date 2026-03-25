# Managed Airflow Platform - User Guide

This guide explains how to use the Managed Airflow Platform to create and manage Apache Airflow deployments across different deployment options.

## Table of Contents

1. [Getting Started](#getting-started)
2. [Understanding Deployment Options](#understanding-deployment-options)
3. [Managing Tenants](#managing-tenants)
4. [Managing Deployments](#managing-deployments)
5. [Managing DAGs](#managing-dags)
6. [Accessing Airflow](#accessing-airflow)
7. [Monitoring and Troubleshooting](#monitoring-and-troubleshooting)
8. [API Reference](#api-reference)
9. [Best Practices](#best-practices)

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

## Understanding Deployment Options

The platform supports **four deployment options**. Choose the one that best fits your needs:

### Quick Comparison

| Feature | Local | Kubernetes | AWS ECS | AWS EC2 |
|---------|-------|------------|---------|---------|
| **Cost** | Free | ~$150/month | ~$137/month | ~$35/month |
| **Complexity** | Simplest | Complex | Easy | Easier |
| **Auto-Scaling** | No | Yes | Yes | Manual |
| **High Availability** | No | High | Medium | Low |
| **Best For** | Learning/Dev | Production | Test/Staging | Dev/Test |

### Local

**Best for learning and local development**

- Runs entirely on your local machine
- Zero cost, no cloud resources needed
- Docker Compose-based deployment
- Perfect for testing and learning
- Fast setup and tear-down

**How it works:**
- Each tenant gets a dedicated directory under `~/airflow-deployments/`
- Airflow components run via docker-compose
- PostgreSQL and Redis run as Docker containers
- Webserver ports dynamically allocated (8080-8180 range)
- Directory-based isolation between tenants

**Use cases:**
- Learning the platform
- Developing and testing DAGs
- Feature development
- Demos without cloud resources

📖 **See [LOCAL_TESTING.md](LOCAL_TESTING.md) for detailed local deployment guide**

### Kubernetes

**Best for production workloads**

- Enterprise-grade multi-tenancy
- High availability with multiple replicas
- KEDA-based auto-scaling
- Flexible resource management
- Multi-cloud support (AWS, GCP, Azure)

**How it works:**
- Each tenant gets a dedicated Kubernetes namespace
- Airflow deployed via Helm charts
- PostgreSQL and Redis deployed as pods
- Workers auto-scale based on queue depth

### AWS ECS (Fargate)

**Best for test/staging environments**

- Serverless containers (no servers to manage)
- Containerized PostgreSQL and Redis
- AWS-native auto-scaling
- Cost-effective (~40% cheaper than managed RDS/ElastiCache)
- Fast deployment (~5 minutes)

**How it works:**
- Each tenant gets a dedicated ECS cluster
- All components run as Fargate tasks
- PostgreSQL data stored on AWS EFS
- Workers auto-scale based on CPU/memory

### AWS EC2 (Docker Compose)

**Best for development and testing**

- Simplest setup with Docker Compose
- One EC2 instance per tenant
- Lowest cost (~$35/month)
- SSH-free management via AWS SSM
- Quick tear-down

**How it works:**
- Each tenant gets a dedicated EC2 instance
- Airflow components run via docker-compose
- PostgreSQL and Redis run as Docker containers
- Instance managed via AWS Systems Manager

## Managing Tenants

Tenants are organizations or teams that use the platform. Each tenant can have multiple Airflow deployments.

### Creating a Tenant

The process is the same regardless of deployment option.

#### Via UI

1. Click **Tenants** in the sidebar
2. Click the **Create Tenant** button
3. Fill in the tenant details:
   - **Tenant ID** - Unique identifier (e.g., "data-team")
   - **Organization Name** - Organization name (e.g., "Data Engineering Team")
4. Click **OK** to create the tenant

#### Via API

```bash
curl -X POST http://localhost:8080/api/v1/tenants \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "data-team",
    "organizationName": "Data Engineering Team"
  }'
```

### What Happens After Creation?

The platform creates isolated resources based on your deployment option:

**Kubernetes:**
- Creates namespace: `airflow-{tenant-id}`
- Example: `airflow-data-team`

**ECS:**
- Creates ECS cluster: `managed-airflow-{tenant-id}`
- Example: `managed-airflow-data-team`

**EC2:**
- Launches EC2 instance with tags: `tenant-id=data-team`
- Installs Docker and Docker Compose
- Configures AWS SSM access

### Viewing Tenants

The Tenants page displays:
- **Tenant ID** - Unique identifier
- **Organization Name** - Organization name
- **Status** - ACTIVE/SUSPENDED/DELETED
- **Created At** - Creation timestamp
- **Actions** - View details, delete

### Deleting a Tenant

1. Click the **Delete** button next to the tenant
2. Confirm the deletion
3. The tenant and **all its deployments** will be deleted

**Warning:** This action cannot be undone. All Airflow deployments for this tenant will be permanently deleted.

## Managing Deployments

Deployments are Apache Airflow instances. The deployment process differs slightly based on your chosen option.

### Creating a Deployment

#### Via UI

1. Click **Deployments** in the sidebar
2. Click the **Create Deployment** button
3. Fill in the deployment details:

##### Basic Information
- **Tenant** - Select the tenant (required)
- **Deployment ID** - Unique ID (e.g., "prod-etl")
- **Deployment Name** - Display name (e.g., "Production ETL Pipeline")
- **Description** - Brief description (optional)

##### Airflow Configuration
- **Airflow Version** - Version to deploy (e.g., "3.1.8")
- **Executor Type** - Choose the executor:
  - **LOCAL** - Simple, runs tasks in scheduler process (dev/test)
  - **CELERY** - Distributed, recommended for production
  - **KUBERNETES** - Each task runs in separate pod (Kubernetes only)
  - **CELERY_KUBERNETES** - Hybrid approach

##### Resource Configuration

**For Local:**
- Resources are determined by Docker's allocated resources
- Docker Compose stack includes all components
- Ports dynamically allocated (8080-8180 for webserver, 5555-5655 for Flower)
- All components share local machine resources

**For Kubernetes and ECS:**
- **Scheduler CPU** - CPU allocation (e.g., "1024" = 1 vCPU)
- **Scheduler Memory** - Memory allocation (e.g., "2048" = 2 GB)
- **Webserver CPU** - CPU allocation (e.g., "512" = 0.5 vCPU)
- **Webserver Memory** - Memory allocation (e.g., "1024" = 1 GB)
- **Worker CPU** - CPU allocation per worker
- **Worker Memory** - Memory allocation per worker
- **Min Workers** - Minimum number of workers (default: 1)
- **Max Workers** - Maximum number of workers (default: 5)

**For EC2:**
- Resources are determined by the EC2 instance type (configured during setup)
- Docker Compose stack includes all components
- Workers scale based on available instance resources

#### Via API

**Local Example:**
```bash
curl -X POST http://localhost:8080/api/v1/deployments \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "my-company",
    "deploymentId": "local-dev",
    "name": "Local Development",
    "description": "Local development environment",
    "airflowVersion": "3.1.8",
    "executorType": "LOCAL",
    "schedulerCpu": "500",
    "schedulerMemory": "1024",
    "webserverCpu": "500",
    "webserverMemory": "1024",
    "workerCpu": "500",
    "workerMemory": "1024",
    "minWorkers": 1,
    "maxWorkers": 3
  }'
```

**Response:**
```json
{
  "deploymentId": "local-dev",
  "tenantId": "my-company",
  "status": "DEPLOYING",
  "airflowVersion": "3.1.8",
  "webserverUrl": "http://localhost:8093"
}
```

**Kubernetes Example:**
```bash
curl -X POST http://localhost:8080/api/v1/deployments \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "data-team",
    "deploymentId": "prod-etl",
    "name": "Production ETL",
    "description": "Main production ETL pipeline",
    "airflowVersion": "3.1.8",
    "executorType": "CELERY",
    "schedulerCpu": "1024",
    "schedulerMemory": "2048",
    "webserverCpu": "512",
    "webserverMemory": "1024",
    "workerCpu": "1024",
    "workerMemory": "2048",
    "minWorkers": 2,
    "maxWorkers": 10,
    "ingressHost": "airflow-prod.example.com"
  }'
```

**ECS Example:**
```bash
curl -X POST http://localhost:8080/api/v1/deployments \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "data-team",
    "deploymentId": "staging-etl",
    "name": "Staging ETL",
    "description": "Staging environment",
    "airflowVersion": "3.1.8",
    "executorType": "CELERY",
    "schedulerCpu": "512",
    "schedulerMemory": "1024",
    "webserverCpu": "256",
    "webserverMemory": "512",
    "workerCpu": "512",
    "workerMemory": "1024",
    "minWorkers": 1,
    "maxWorkers": 5
  }'
```

**EC2 Example:**
```bash
curl -X POST http://localhost:8080/api/v1/deployments \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "data-team",
    "deploymentId": "dev-etl",
    "name": "Development ETL",
    "description": "Development environment",
    "airflowVersion": "3.1.8",
    "executorType": "LOCAL",
    "schedulerCpu": "1000",
    "schedulerMemory": "2048",
    "webserverCpu": "500",
    "webserverMemory": "1024"
  }'
```

### What Happens During Deployment?

**Kubernetes (~5-10 minutes):**
1. Helm chart installed in tenant namespace
2. PostgreSQL and Redis pods deployed
3. Airflow scheduler, webserver, and workers deployed
4. KEDA ScaledObject configured for auto-scaling
5. Ingress configured (if specified)
6. Status changes: PENDING → DEPLOYING → RUNNING

**ECS (~5-7 minutes):**
1. PostgreSQL task deployed with EFS volume
2. Redis task deployed (if Celery executor)
3. Wait for PostgreSQL to be stable
4. Airflow tasks (scheduler, webserver, workers) deployed
5. Application Auto Scaling policies configured
6. Status changes: PENDING → DEPLOYING → RUNNING

**EC2 (~3-5 minutes):**
1. Docker Compose file generated
2. File transferred to EC2 instance via SSM
3. `docker-compose up -d` executed via SSM
4. Containers start (postgres, redis, scheduler, webserver, workers)
5. Status changes: PENDING → DEPLOYING → RUNNING

### Viewing Deployments

The Deployments page displays:
- **Deployment ID** - Unique identifier
- **Name** - Display name
- **Tenant ID** - Owner tenant
- **Airflow Version** - Version deployed
- **Executor** - Executor type
- **Status** - Current status
- **Workers** - Min-Max worker count
- **Created At** - Creation timestamp
- **Actions** - Open UI, view details, delete

### Deployment Status

| Status | Description |
|--------|-------------|
| PENDING | Deployment created, waiting to start |
| DEPLOYING | Resources being created |
| RUNNING | Deployment is healthy and running |
| UPDATING | Configuration is being updated |
| FAILED | Deployment failed, check logs |
| STOPPED | Deployment is stopped |
| DELETED | Deployment has been removed |

### Updating a Deployment

```bash
curl -X PUT http://localhost:8080/api/v1/deployments/{deploymentId} \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "data-team",
    "name": "Production ETL",
    "description": "Updated description",
    "airflowVersion": "3.1.8",
    "executorType": "CELERY",
    "minWorkers": 3,
    "maxWorkers": 15,
    "schedulerCpu": "2048",
    "schedulerMemory": "4096"
  }'
```

**Update behavior by option:**
- **Kubernetes:** Helm upgrade with rolling update
- **ECS:** Task definitions updated, services updated with rolling deployment
- **EC2:** Docker Compose file regenerated, containers recreated

### Scaling Workers

```bash
curl -X POST http://localhost:8080/api/v1/deployments/{deploymentId}/scale \
  -H "Content-Type: application/json" \
  -d '{
    "minWorkers": 2,
    "maxWorkers": 10
  }'
```

**Scaling behavior:**
- **Kubernetes:** KEDA ScaledObject updated
- **ECS:** Application Auto Scaling target updated
- **EC2:** Docker Compose updated, requires restart

### Deleting a Deployment

1. Click the **Delete** button next to the deployment
2. Confirm the deletion
3. All resources will be removed

**Warning:** This will delete all Airflow components and metadata. DAGs and task history will be lost unless backed up.

## Managing DAGs

The platform provides a comprehensive web-based interface for creating, editing, and deploying DAGs directly to your Airflow instances.

### Overview

DAGs (Directed Acyclic Graphs) define your workflows in Airflow. Instead of manually uploading DAG files to Airflow, you can use the platform's UI to:

- Write DAGs using a Monaco code editor (same editor used in VS Code)
- Validate DAG code before deployment
- Associate DAGs with Git repositories for version control
- Deploy DAGs directly to Airflow instances
- Track DAG status and deployment history

### Creating a DAG

#### Via UI

1. Click **DAGs** in the sidebar
2. Click the **Create DAG** button
3. Fill in the DAG details:

##### Basic Information
- **Deployment** - Select the Airflow deployment where this DAG will run (required)
- **DAG Name** - Human-readable name (e.g., "Daily ETL Pipeline")
- **Description** - Brief description of what the DAG does
- **File Name** - Python file name, must end with `.py` (e.g., "daily_etl_dag.py")

##### DAG Code
- Write your Airflow DAG code in the Monaco editor
- Full Python syntax highlighting
- Auto-completion and error detection
- A default template is provided for new DAGs

**Default DAG Template:**
```python
from datetime import datetime, timedelta
from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.operators.bash import BashOperator

default_args = {
    'owner': 'airflow',
    'depends_on_past': False,
    'email_on_failure': False,
    'email_on_retry': False,
    'retries': 1,
    'retry_delay': timedelta(minutes=5),
}

dag = DAG(
    'my_sample_dag',
    default_args=default_args,
    description='A simple sample DAG',
    schedule_interval=timedelta(days=1),
    start_date=datetime(2024, 1, 1),
    catchup=False,
    tags=['example'],
)

def print_hello():
    print('Hello from Airflow!')
    return 'Hello task completed'

hello_task = PythonOperator(
    task_id='hello_task',
    python_callable=print_hello,
    dag=dag,
)

bash_task = BashOperator(
    task_id='bash_task',
    bash_command='echo "Running bash task"',
    dag=dag,
)

hello_task >> bash_task
```

##### Git Configuration (Optional)
- **Git Repository** - URL of your Git repository (e.g., `https://github.com/user/airflow-dags.git`)
- **Git Branch** - Branch name (default: "main")
- **Git Path** - Path within the repository where the DAG file is located (e.g., "dags/")

##### Additional Metadata
- **Owner** - Team or person responsible for the DAG
- **Tags** - Comma-separated tags for organization (e.g., "etl, production, daily")
- **Initial State** - Whether the DAG should be Active or Paused when first deployed

4. Click **Create DAG** to save

#### Via API

```bash
curl -X POST http://localhost:8080/api/v1/dags \
  -H "Content-Type: application/json" \
  -d '{
    "deploymentId": "prod-etl",
    "name": "Daily ETL Pipeline",
    "description": "Processes daily data from sources",
    "fileName": "daily_etl_dag.py",
    "dagCode": "from datetime import datetime, timedelta\nfrom airflow import DAG\nfrom airflow.operators.bash import BashOperator\n\ndefault_args = {\n    \"owner\": \"data-team\",\n    \"start_date\": datetime(2024, 1, 1),\n}\n\ndag = DAG(\n    \"daily_etl\",\n    default_args=default_args,\n    schedule_interval=\"@daily\",\n    catchup=False,\n)\n\ntask = BashOperator(\n    task_id=\"process_data\",\n    bash_command=\"echo Processing data...\",\n    dag=dag,\n)",
    "gitRepository": "https://github.com/mycompany/airflow-dags.git",
    "gitBranch": "main",
    "gitPath": "dags/",
    "owner": "data-team",
    "tags": "etl,daily,production",
    "isPaused": false
  }'
```

**Response:**
```json
{
  "dagId": "daily_etl_a1b2c3d4",
  "deploymentId": "prod-etl",
  "deploymentName": "Production ETL",
  "name": "Daily ETL Pipeline",
  "fileName": "daily_etl_dag.py",
  "status": "VALID",
  "validationErrors": null,
  "createdAt": "2024-01-15T10:30:00",
  "updatedAt": "2024-01-15T10:30:00"
}
```

### DAG Validation

After creating or updating a DAG, the platform automatically validates the code:

**Validation Checks:**
- Required Airflow imports present
- DAG object definition exists
- Basic Python syntax (balanced parentheses, basic structure)
- Import statements present

**Validation Results:**
- **VALID** - DAG passed validation and can be deployed
- **INVALID** - DAG has validation errors (shown in UI and stored in database)

**Viewing Validation Errors:**
- Via UI: Red alert box displays errors on DAG details page
- Via API: Check the `validationErrors` field in the response

### Viewing DAGs

#### Via UI

1. Click **DAGs** in the sidebar
2. The DAG listing page shows all DAGs across all deployments

**Displayed Information:**
- DAG ID, Name, Deployment
- File Name, Status, Owner
- Git Repository and Branch
- Last Deployed timestamp
- Created timestamp

**Filter by Deployment:**
- Use the dropdown to filter DAGs by specific deployment
- Select "All Deployments" to see everything

#### Via API

**Get all DAGs:**
```bash
curl http://localhost:8080/api/v1/dags
```

**Get DAGs for a specific deployment:**
```bash
curl http://localhost:8080/api/v1/dags/deployment/prod-etl
```

**Get specific DAG:**
```bash
curl http://localhost:8080/api/v1/dags/{dagId}
```

### Editing a DAG

#### Via UI

1. Go to the DAGs page
2. Click **Edit** button next to the DAG
3. Modify the DAG details or code in the Monaco editor
4. Click **Update DAG** to save changes

**Note:** Editing a DAG will re-trigger validation. The status will change to VALID or INVALID based on the validation result.

#### Via API

```bash
curl -X PUT http://localhost:8080/api/v1/dags/{dagId} \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Updated DAG Name",
    "description": "Updated description",
    "dagCode": "# Updated code here...",
    "owner": "new-team"
  }'
```

### Deploying a DAG

Once a DAG is VALID, you can deploy it to the Airflow instance.

#### Via UI

1. Go to the DAGs page
2. Find a DAG with status **VALID**
3. Click the **Deploy** button
4. Confirm deployment
5. Status changes to DEPLOYING, then DEPLOYED

**Or from DAG Details:**
1. View a DAG with status **VALID**
2. Click the **Deploy to Airflow** button at top right
3. Status changes to DEPLOYING, then DEPLOYED

#### Via API

```bash
curl -X POST http://localhost:8080/api/v1/dags/{dagId}/deploy
```

**Response:**
```json
{
  "dagId": "daily_etl_a1b2c3d4",
  "status": "DEPLOYED",
  "lastDeployedAt": "2024-01-15T10:35:00"
}
```

### Running/Triggering a DAG

Once a DAG is deployed, you can trigger it to run immediately.

#### Via UI

**From DAG Listing Page:**
1. Go to the DAGs page
2. Find a DAG with status **DEPLOYED**
3. Click the green **Run** button
4. Confirm "Trigger this DAG to run now?"
5. Success message appears when triggered
6. Go to Airflow UI to see the run

**From DAG Details Page:**
1. View a DAG with status **DEPLOYED**
2. Click the green **Run DAG** button at top right
3. Success message appears when triggered

#### Via API

```bash
curl -X POST http://localhost:8080/api/v1/dags/{dagId}/trigger
```

**Response:**
```json
{
  "success": true,
  "message": "DAG run triggered successfully",
  "airflowDagId": "my_sample_dag",
  "response": {
    "dag_run_id": "manual_1703001234567",
    "dag_id": "my_sample_dag",
    "state": "queued",
    "execution_date": "2024-01-15T10:30:00+00:00"
  }
}
```

**What Happens When You Trigger:**
1. Platform extracts the DAG ID from your Python code
2. Calls Airflow REST API to create a new DAG run
3. Airflow queues the run with ID: `manual_{timestamp}`
4. Tasks start executing based on DAG schedule and dependencies
5. You can monitor progress in Airflow UI

**Viewing the Run in Airflow:**
1. Open Airflow UI (click "Open" on deployment)
2. Navigate to your DAG
3. Click on "Runs" tab
4. Find the run with ID `manual_*`
5. Monitor task execution in Grid/Graph view

### DAG Status Lifecycle

| Status | Description | Actions Available |
|--------|-------------|-------------------|
| **DRAFT** | Newly created, not validated yet | Edit, Delete |
| **VALIDATING** | Being validated | Wait |
| **VALID** | Passed validation | Edit, Deploy, Delete |
| **INVALID** | Failed validation | Edit, Delete |
| **DEPLOYING** | Being deployed to Airflow | Wait |
| **DEPLOYED** | Successfully deployed | **Run**, View in Airflow, Edit, Delete |
| **FAILED** | Deployment failed | Edit, Retry, Delete |
| **DELETING** | Being deleted | Wait |
| **DELETED** | Removed | None |

### Deleting a DAG

#### Via UI

1. Go to the DAGs page
2. Click **Delete** button next to the DAG
3. Confirm deletion
4. DAG will be removed from the platform and from Airflow (if deployed)

#### Via API

```bash
curl -X DELETE http://localhost:8080/api/v1/dags/{dagId}
```

**Warning:** This removes the DAG from both the platform database and the Airflow deployment.

### Viewing DAG Code

#### Via UI

**Read-Only View:**
1. Go to DAGs page
2. Click **View** button next to a DAG
3. DAG details page shows all information including code

The code is displayed in a read-only Monaco editor with syntax highlighting.

### Git Integration

DAGs can optionally be linked to Git repositories for version control.

**Current Features:**
- Store Git repository URL, branch, and path
- Track which Git repo each DAG comes from
- Manually configure Git settings per DAG

**Planned Features (Not Yet Implemented):**
- Automatic Git-sync to sync changes from Git to Airflow
- Two-way sync (UI changes push to Git)
- Commit tracking and version history
- Pull request integration

**Recommended Workflow:**
1. Create DAG in platform UI
2. Test and validate in development deployment
3. Push to Git repository manually
4. Update Git settings in DAG
5. Use Git as source of truth
6. Platform maintains metadata and deployment state

### Best Practices for DAG Management

1. **Naming Conventions:**
   - Use descriptive names for DAGs
   - Include environment or purpose in filename
   - Example: `prod_customer_etl_dag.py`, `staging_ml_training_dag.py`

2. **Validation:**
   - Always check for VALID status before deploying
   - Fix validation errors immediately
   - Test DAG code locally before creating in platform

3. **Git Integration:**
   - Use Git repositories for version control
   - Maintain DAG code in version control
   - Document Git settings in DAG metadata

4. **Organization:**
   - Use tags to categorize DAGs (e.g., "etl", "ml", "reporting")
   - Set appropriate owners for each DAG
   - Use deployment filters to focus on specific environments

5. **Testing:**
   - Test new DAGs in development/staging deployments first
   - Verify DAG appears in Airflow UI after deployment
   - Check Airflow logs for any import errors
   - Trigger a test run to ensure tasks execute correctly
   - Monitor the first few runs for any issues

6. **Code Quality:**
   - Follow Airflow best practices
   - Use appropriate operators for tasks
   - Implement proper error handling
   - Add meaningful descriptions and documentation

7. **Security:**
   - Avoid hardcoding credentials in DAG code
   - Use Airflow Connections and Variables
   - Review code for security vulnerabilities
   - Limit access to production DAGs

### Troubleshooting DAG Issues

#### DAG Shows as INVALID

**Check validation errors:**
- Via UI: View the DAG details page
- Via API: Check `validationErrors` field

**Common Issues:**
- Missing Airflow imports
- Syntax errors in Python code
- Unbalanced parentheses or brackets
- No DAG object defined

**Solution:** Edit the DAG code to fix the reported errors and save again.

#### DAG Won't Deploy

**Possible causes:**
- DAG status is not VALID
- Deployment is not in RUNNING status
- Network issues with Airflow instance

**Solution:**
1. Verify DAG is VALID
2. Check deployment status is RUNNING
3. Check platform logs for errors

#### DAG Not Appearing in Airflow UI

**Note:** Current implementation stores DAG in platform database but does not automatically deploy to Airflow's DAG folder.

**Workaround (until Git-sync is implemented):**
- Manually copy DAG code to Airflow DAG folder
- Use methods described in "Uploading DAGs" section
- Alternatively, set up your own Git-sync process

**Future Enhancement:**
The platform will automatically deploy DAGs to Airflow using one of these methods:
- Kubernetes ConfigMap/Secret
- Git-sync sidecar container
- S3/GCS bucket with sync
- Persistent Volume (PV) mount

#### Cannot Trigger DAG Run

**Symptoms:**
- "Run" button doesn't appear
- Error message when triggering
- DAG run doesn't start in Airflow

**Possible causes:**
1. DAG status is not DEPLOYED
2. Airflow webserver URL not configured
3. DAG ID cannot be extracted from code
4. Airflow API authentication failed

**Solution:**
1. **Check DAG status:**
   - Ensure status is DEPLOYED (not VALID or other)
   - Deploy the DAG first if needed

2. **Verify webserver URL:**
   - Check deployment has a valid webserver URL
   - Test URL accessibility: `curl {webserver-url}/health`

3. **Check DAG ID in code:**
   - Ensure DAG has a clear `dag_id` parameter or first argument
   - Example: `DAG('my_dag_id', ...)` or `dag_id='my_dag_id'`

4. **Check Airflow credentials:**
   - Default: admin/admin
   - Verify in Airflow: Settings → Users

5. **Check logs:**
```bash
# Backend logs will show trigger errors
cd control-plane
./mvnw spring-boot:run
# Look for "Failed to trigger DAG run" messages
```

6. **Manual test:**
```bash
# Test Airflow API directly
curl -X POST {webserver-url}/api/v1/dags/{dag_id}/dagRuns \
  -H "Content-Type: application/json" \
  -u admin:admin \
  -d '{"dag_run_id": "test_123"}'
```

## Accessing Airflow

### Getting the Airflow UI URL

**Via UI:**
1. Go to Deployments page
2. Find your deployment (status must be RUNNING)
3. Click the **Open** button in Actions column
4. Airflow UI opens in new tab

**Via API:**
```bash
curl http://localhost:8080/api/v1/deployments/{deploymentId}
```

Look for the `webserverUrl` field in the response.

### Accessing Airflow by Deployment Option

#### Local

For local deployments, the webserver URL is returned when creating the deployment:

```bash
# Get webserver URL
curl http://localhost:8080/api/v1/deployments/{deployment-id} | jq .webserverUrl

# Example output: http://localhost:8093
```

**Access the UI:**
- Open the URL in your browser (e.g., `http://localhost:8093`)
- Each deployment gets a unique port (8080-8180 range)

**Default credentials:**
- Username: `admin`
- Password: `admin`

**Monitor logs:**
```bash
cd ~/airflow-deployments/{tenant-id}/{deployment-id}
docker-compose logs -f
```

**Access container:**
```bash
cd ~/airflow-deployments/{tenant-id}/{deployment-id}
docker-compose exec airflow-webserver bash
```

#### Kubernetes

**If ingress is configured:**
```
https://airflow-prod.example.com
```

**Without ingress (port-forward):**
```bash
# Get namespace
NAMESPACE=airflow-{tenant-id}

# List services
kubectl get svc -n $NAMESPACE

# Port forward to webserver
kubectl port-forward -n $NAMESPACE svc/{deployment-id}-webserver 8080:8080

# Access at http://localhost:8080
```

**Get credentials:**
```bash
# Username: admin (default)
# Password:
kubectl get secret -n $NAMESPACE {deployment-id}-webserver-secret \
  -o jsonpath='{.data.webserver-secret-key}' | base64 -d
```

#### AWS ECS

**If ALB is configured:**
```
http://{deployment-id}-alb.{region}.elb.amazonaws.com
```

**Using ECS Exec:**
```bash
# Get cluster and task
CLUSTER=managed-airflow-{tenant-id}
TASK_ID=$(aws ecs list-tasks --cluster $CLUSTER --service-name {deployment-id}-webserver --query 'taskArns[0]' --output text)

# Get task's public IP
aws ecs describe-tasks --cluster $CLUSTER --tasks $TASK_ID \
  --query 'tasks[0].attachments[0].details[?name==`networkInterfaceId`].value' --output text | \
  xargs -I {} aws ec2 describe-network-interfaces --network-interface-ids {} \
  --query 'NetworkInterfaces[0].Association.PublicIp' --output text

# Access at http://{PUBLIC_IP}:8080
```

**Default credentials:**
- Username: `admin`
- Password: `airflow` (from environment variables)

#### AWS EC2

**Get instance IP:**
```bash
INSTANCE_ID=$(aws ec2 describe-instances \
  --filters "Name=tag:tenant-id,Values={tenant-id}" \
            "Name=instance-state-name,Values=running" \
  --query 'Reservations[0].Instances[0].InstanceId' \
  --output text)

PUBLIC_IP=$(aws ec2 describe-instances \
  --instance-ids $INSTANCE_ID \
  --query 'Reservations[0].Instances[0].PublicIpAddress' \
  --output text)

echo "Airflow UI: http://$PUBLIC_IP:8080"
```

**Access via ALB (if configured):**
```
http://{tenant-id}-alb.{region}.elb.amazonaws.com
```

**Default credentials:**
- Username: `admin`
- Password: `airflow` (from docker-compose.yml environment)

### Uploading DAGs

#### Local

For local deployments, simply copy DAG files to the deployment directory:

```bash
# Navigate to DAGs directory
cd ~/airflow-deployments/{tenant-id}/{deployment-id}/dags

# Copy DAG file
cp /path/to/your/my_dag.py .

# Or create directly
cat > my_dag.py << 'EOF'
from datetime import datetime, timedelta
from airflow import DAG
from airflow.operators.bash import BashOperator

default_args = {
    'owner': 'airflow',
    'depends_on_past': False,
    'start_date': datetime(2024, 1, 1),
}

dag = DAG(
    'my_local_dag',
    default_args=default_args,
    description='Example DAG for local testing',
    schedule_interval=timedelta(days=1),
)

t1 = BashOperator(
    task_id='hello_world',
    bash_command='echo "Hello from local Airflow!"',
    dag=dag,
)
EOF
```

Airflow automatically detects new DAGs within 30 seconds (default DAG scan interval).

**Verify DAG loaded:**
```bash
cd ~/airflow-deployments/{tenant-id}/{deployment-id}
docker-compose exec airflow-scheduler airflow dags list
```

#### Kubernetes

**Option 1: Using kubectl cp**
```bash
kubectl cp my_dag.py $NAMESPACE/{scheduler-pod}:/opt/airflow/dags/
```

**Option 2: Git-sync (recommended)**
Configure git-sync sidecar in Helm values to automatically sync DAGs from Git.

**Option 3: Persistent Volume**
Mount shared PVC to all Airflow pods and copy DAGs there.

#### ECS

**Option 1: S3 Bucket**
Store DAGs in S3 and configure Airflow to sync from S3:
```python
# In airflow.cfg or environment variables
AIRFLOW__CORE__DAGS_FOLDER = s3://my-bucket/dags
```

**Option 2: ECS Exec**
```bash
# Copy DAG to scheduler task
aws ecs execute-command --cluster $CLUSTER \
  --task $SCHEDULER_TASK_ID \
  --container airflow-scheduler \
  --command "/bin/bash"

# Inside container
cat > /opt/airflow/dags/my_dag.py << 'EOF'
# DAG code here
EOF
```

#### EC2

**Option 1: SSH/SCP (if SSH key available)**
```bash
scp -i key.pem my_dag.py ec2-user@$PUBLIC_IP:/home/ec2-user/airflow/dags/
```

**Option 2: AWS SSM (SSH-free)**
```bash
# Create DAG file via SSM
aws ssm send-command \
  --instance-ids $INSTANCE_ID \
  --document-name "AWS-RunShellScript" \
  --parameters 'commands=["cat > /home/ec2-user/airflow/dags/my_dag.py <<EOF
from airflow import DAG
# DAG code here
EOF"]'
```

**Option 3: Git repository**
Clone your DAGs repo on the EC2 instance and set up git-sync or cron job.

## Monitoring and Troubleshooting

### Checking Deployment Status

**Via UI:**
- Navigate to Deployments page
- Check the Status column
- Green = RUNNING, Red = FAILED, Yellow = DEPLOYING

**Via API:**
```bash
curl http://localhost:8080/api/v1/deployments/{deploymentId}
```

### Viewing Logs

#### Local

```bash
cd ~/airflow-deployments/{tenant-id}/{deployment-id}

# All services
docker-compose logs -f

# Specific service
docker-compose logs -f airflow-scheduler
docker-compose logs -f airflow-webserver
docker-compose logs -f airflow-worker

# Last 100 lines
docker-compose logs --tail=100 airflow-scheduler

# Follow specific service
docker-compose logs -f --tail=100 airflow-scheduler
```

**Check container status:**
```bash
docker-compose ps
```

**Check resource usage:**
```bash
docker stats
```

#### Kubernetes

```bash
NAMESPACE=airflow-{tenant-id}

# Scheduler logs
kubectl logs -n $NAMESPACE -l component=scheduler --tail=100 -f

# Webserver logs
kubectl logs -n $NAMESPACE -l component=webserver --tail=100 -f

# Worker logs
kubectl logs -n $NAMESPACE -l component=worker --tail=100 -f

# All pods
kubectl logs -n $NAMESPACE --all-containers=true --tail=100 -f
```

#### ECS

```bash
CLUSTER=managed-airflow-{tenant-id}
DEPLOYMENT_ID={deployment-id}

# Scheduler logs
aws logs tail /ecs/managed-airflow/$DEPLOYMENT_ID/scheduler --follow

# Webserver logs
aws logs tail /ecs/managed-airflow/$DEPLOYMENT_ID/webserver --follow

# Worker logs
aws logs tail /ecs/managed-airflow/$DEPLOYMENT_ID/worker --follow

# PostgreSQL logs
aws logs tail /ecs/managed-airflow/$DEPLOYMENT_ID/postgres --follow
```

#### EC2

```bash
# SSH to instance
ssh -i key.pem ec2-user@$PUBLIC_IP

# View all container logs
docker-compose logs -f

# Specific component
docker-compose logs -f scheduler
docker-compose logs -f webserver
docker-compose logs -f worker

# Or via SSM (SSH-free)
aws ssm start-session --target $INSTANCE_ID

# Once in session
docker-compose logs -f
```

### Common Issues

#### 1. Deployment Stuck in DEPLOYING

**Kubernetes:**
```bash
# Check pod status
kubectl get pods -n $NAMESPACE

# Check pod events
kubectl describe pod -n $NAMESPACE {pod-name}

# Check pod logs
kubectl logs -n $NAMESPACE {pod-name}

# Common causes:
# - Insufficient cluster resources
# - Image pull errors
# - PVC provisioning issues
```

**ECS:**
```bash
# Check service status
aws ecs describe-services --cluster $CLUSTER --services {deployment-id}-scheduler

# Check stopped tasks (failures)
aws ecs list-tasks --cluster $CLUSTER --desired-status STOPPED

# Describe failed task
aws ecs describe-tasks --cluster $CLUSTER --tasks {task-arn}

# Common causes:
# - EFS mount failed
# - Insufficient Fargate capacity
# - Task execution role permissions
```

**EC2:**
```bash
# Check instance status
aws ec2 describe-instance-status --instance-ids $INSTANCE_ID

# Check SSM command history
aws ssm list-commands --instance-id $INSTANCE_ID

# View command output
aws ssm get-command-invocation --command-id {command-id} --instance-id $INSTANCE_ID

# Common causes:
# - Docker not installed
# - Docker Compose failed
# - Port conflicts
# - Instance out of resources
```

#### 2. Workers Not Autoscaling

**Kubernetes:**
```bash
# Check KEDA installation
kubectl get pods -n keda

# Check ScaledObject
kubectl get scaledobject -n $NAMESPACE
kubectl describe scaledobject -n $NAMESPACE {name}

# Check HPA (created by KEDA)
kubectl get hpa -n $NAMESPACE

# Check worker pods
kubectl get pods -n $NAMESPACE -l component=worker
```

**ECS:**
```bash
# Check auto-scaling target
aws application-autoscaling describe-scalable-targets \
  --service-namespace ecs \
  --resource-ids service/$CLUSTER/{deployment-id}-worker

# Check scaling policies
aws application-autoscaling describe-scaling-policies \
  --service-namespace ecs \
  --resource-id service/$CLUSTER/{deployment-id}-worker

# View scaling activities
aws application-autoscaling describe-scaling-activities \
  --service-namespace ecs \
  --resource-id service/$CLUSTER/{deployment-id}-worker

# Check current worker count
aws ecs describe-services --cluster $CLUSTER --services {deployment-id}-worker
```

**EC2:**
Workers don't auto-scale on EC2 Docker Compose deployments. To scale:
```bash
ssh -i key.pem ec2-user@$PUBLIC_IP

# Edit docker-compose.yml
vim docker-compose.yml

# Update worker replicas:
# services:
#   worker:
#     deploy:
#       replicas: 3  # Change this

# Restart workers
docker-compose up -d --scale worker=3
```

#### 3. Cannot Access Airflow UI

**Kubernetes:**
```bash
# Check webserver service
kubectl get svc -n $NAMESPACE | grep webserver

# Check webserver pods
kubectl get pods -n $NAMESPACE -l component=webserver

# Check webserver logs
kubectl logs -n $NAMESPACE -l component=webserver

# Port-forward to test
kubectl port-forward -n $NAMESPACE svc/{deployment-id}-webserver 8080:8080

# If using ingress, check ingress
kubectl get ingress -n $NAMESPACE
kubectl describe ingress -n $NAMESPACE {ingress-name}
```

**ECS:**
```bash
# Check webserver service
aws ecs describe-services --cluster $CLUSTER --services {deployment-id}-webserver

# Check webserver tasks
aws ecs list-tasks --cluster $CLUSTER --service-name {deployment-id}-webserver

# Check task's public IP
aws ecs describe-tasks --cluster $CLUSTER --tasks {task-arn}

# Check security group
aws ec2 describe-security-groups --group-ids {sg-id}

# Ensure port 8080 is open
```

**EC2:**
```bash
# Check if webserver container is running
ssh -i key.pem ec2-user@$PUBLIC_IP 'docker ps | grep webserver'

# Check webserver logs
ssh -i key.pem ec2-user@$PUBLIC_IP 'docker-compose logs webserver'

# Check security group allows port 8080
aws ec2 describe-security-groups --group-ids {sg-id}

# Test locally on instance
ssh -i key.pem ec2-user@$PUBLIC_IP 'curl http://localhost:8080'
```

#### 4. Tasks Not Running

**All Options:**
1. Check scheduler logs (see "Viewing Logs" section)
2. Verify DAGs are loaded in Airflow UI → Admin → DAGs
3. Check database connectivity
4. Verify worker connectivity
5. For Celery executor, check Redis/broker connectivity

**Kubernetes:**
```bash
# Check scheduler
kubectl logs -n $NAMESPACE -l component=scheduler | grep ERROR

# Check database (if deployed in cluster)
kubectl logs -n $NAMESPACE -l component=postgresql

# Check Redis (if using Celery)
kubectl logs -n $NAMESPACE -l component=redis
```

**ECS:**
```bash
# Check scheduler logs
aws logs tail /ecs/managed-airflow/{deployment-id}/scheduler --follow | grep ERROR

# Check PostgreSQL logs
aws logs tail /ecs/managed-airflow/{deployment-id}/postgres --follow

# Check Redis logs (if using Celery)
aws logs tail /ecs/managed-airflow/{deployment-id}/redis --follow
```

**EC2:**
```bash
ssh -i key.pem ec2-user@$PUBLIC_IP

# Check all containers
docker-compose ps

# Check scheduler
docker-compose logs scheduler | grep ERROR

# Check database
docker-compose logs postgres

# Check Redis (if using Celery)
docker-compose logs redis

# Verify connectivity
docker exec {scheduler-container} ping postgres
docker exec {scheduler-container} ping redis
```

### Resource Usage

**Kubernetes:**
```bash
# Pod resource usage
kubectl top pods -n $NAMESPACE

# Node resource usage
kubectl top nodes
```

**ECS:**
```bash
# View CloudWatch metrics
aws cloudwatch get-metric-statistics \
  --namespace AWS/ECS \
  --metric-name CPUUtilization \
  --dimensions Name=ClusterName,Value=$CLUSTER \
               Name=ServiceName,Value={deployment-id}-scheduler \
  --start-time 2024-01-01T00:00:00Z \
  --end-time 2024-01-01T23:59:59Z \
  --period 3600 \
  --statistics Average
```

**EC2:**
```bash
# Instance metrics (via CloudWatch)
aws cloudwatch get-metric-statistics \
  --namespace AWS/EC2 \
  --metric-name CPUUtilization \
  --dimensions Name=InstanceId,Value=$INSTANCE_ID \
  --start-time 2024-01-01T00:00:00Z \
  --end-time 2024-01-01T23:59:59Z \
  --period 3600 \
  --statistics Average

# Container resource usage (SSH to instance)
ssh -i key.pem ec2-user@$PUBLIC_IP 'docker stats --no-stream'
```

## API Reference

The platform exposes a REST API for programmatic access.

### Base URL

```
http://localhost:8080/api/v1
```

### API Documentation

Swagger UI: `http://localhost:8080/swagger-ui.html`

OpenAPI Spec: `http://localhost:8080/v3/api-docs`

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
  "tenantId": "engineering-team",
  "organizationName": "Engineering Team"
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
  "deploymentId": "prod-airflow",
  "name": "Production Airflow",
  "description": "Main production instance",
  "airflowVersion": "3.1.8",
  "executorType": "CELERY",
  "minWorkers": 1,
  "maxWorkers": 5,
  "schedulerCpu": "1024",
  "schedulerMemory": "2048",
  "workerCpu": "1024",
  "workerMemory": "2048",
  "webserverCpu": "512",
  "webserverMemory": "1024",
  "ingressHost": "airflow.example.com"
}
```

**Update deployment:**
```bash
PUT /api/v1/deployments/{deploymentId}
Content-Type: application/json

{
  "tenantId": "engineering-team",
  "name": "Production Airflow",
  "description": "Updated description",
  "airflowVersion": "3.1.8",
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

**Scale deployment:**
```bash
POST /api/v1/deployments/{deploymentId}/scale
Content-Type: application/json

{
  "minWorkers": 2,
  "maxWorkers": 10
}
```

#### DAGs

**List all DAGs:**
```bash
GET /api/v1/dags
```

**Get DAG by ID:**
```bash
GET /api/v1/dags/{dagId}
```

**Get DAGs for a deployment:**
```bash
GET /api/v1/dags/deployment/{deploymentId}
```

**Create DAG:**
```bash
POST /api/v1/dags
Content-Type: application/json

{
  "deploymentId": "prod-etl",
  "name": "Daily ETL Pipeline",
  "description": "Processes daily data from sources",
  "fileName": "daily_etl_dag.py",
  "dagCode": "from airflow import DAG\n# DAG code here...",
  "gitRepository": "https://github.com/mycompany/airflow-dags.git",
  "gitBranch": "main",
  "gitPath": "dags/",
  "owner": "data-team",
  "tags": "etl,daily,production",
  "isPaused": false
}
```

**Update DAG:**
```bash
PUT /api/v1/dags/{dagId}
Content-Type: application/json

{
  "name": "Updated DAG Name",
  "description": "Updated description",
  "dagCode": "# Updated code...",
  "owner": "new-team"
}
```

**Delete DAG:**
```bash
DELETE /api/v1/dags/{dagId}
```

**Deploy DAG to Airflow:**
```bash
POST /api/v1/dags/{dagId}/deploy
```

**Trigger DAG run:**
```bash
POST /api/v1/dags/{dagId}/trigger
```

Response:
```json
{
  "success": true,
  "message": "DAG run triggered successfully",
  "airflowDagId": "my_dag",
  "response": {
    "dag_run_id": "manual_1234567890",
    "state": "queued"
  }
}
```

### Complete Example: Create Tenant, Deployment, and DAG

```bash
# 1. Create tenant
TENANT_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/tenants \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "data-team",
    "organizationName": "Data Team"
  }')

echo "Tenant created: $TENANT_RESPONSE"

# 2. Wait for tenant to be ready
sleep 10

# 3. Create deployment
DEPLOYMENT_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/deployments \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "data-team",
    "deploymentId": "my-airflow",
    "name": "My Airflow Instance",
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
  }')

echo "Deployment created: $DEPLOYMENT_RESPONSE"
DEPLOYMENT_ID=$(echo $DEPLOYMENT_RESPONSE | jq -r '.deploymentId')

# 4. Wait for deployment to be ready
while true; do
  STATUS=$(curl -s http://localhost:8080/api/v1/deployments/$DEPLOYMENT_ID | jq -r '.status')
  echo "Deployment status: $STATUS"
  if [ "$STATUS" = "RUNNING" ]; then
    break
  fi
  sleep 30
done

# 5. Create DAG
DAG_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/dags \
  -H "Content-Type: application/json" \
  -d '{
    "deploymentId": "'$DEPLOYMENT_ID'",
    "name": "My First DAG",
    "description": "A simple example DAG",
    "fileName": "my_first_dag.py",
    "dagCode": "from datetime import datetime, timedelta\nfrom airflow import DAG\nfrom airflow.operators.bash import BashOperator\n\ndefault_args = {\"owner\": \"airflow\", \"start_date\": datetime(2024, 1, 1)}\ndag = DAG(\"my_first_dag\", default_args=default_args, schedule_interval=\"@daily\", catchup=False)\ntask = BashOperator(task_id=\"hello\", bash_command=\"echo Hello Airflow\", dag=dag)",
    "owner": "data-team",
    "tags": "example,test",
    "isPaused": false
  }')

echo "DAG created: $DAG_RESPONSE"
DAG_ID=$(echo $DAG_RESPONSE | jq -r '.dagId')

# 6. Deploy DAG to Airflow
if [ "$(echo $DAG_RESPONSE | jq -r '.status')" = "VALID" ]; then
  DEPLOY_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/dags/$DAG_ID/deploy)
  echo "DAG deployed: $DEPLOY_RESPONSE"

  # 7. Wait for deployment to complete
  sleep 5

  # 8. Trigger DAG run
  TRIGGER_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/dags/$DAG_ID/trigger)
  echo "DAG run triggered: $TRIGGER_RESPONSE"

  if [ "$(echo $TRIGGER_RESPONSE | jq -r '.success')" = "true" ]; then
    echo "Success! DAG is running in Airflow"
    echo "View run in Airflow UI at: $(curl -s http://localhost:8080/api/v1/deployments/$DEPLOYMENT_ID | jq -r '.webserverUrl')"
  fi
else
  echo "DAG validation failed, check errors"
fi
```

### Complete Example: Create Tenant and Deployment

```bash
# 1. Create tenant
TENANT_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/tenants \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "data-team",
    "organizationName": "Data Team"
  }')

echo "Tenant created: $TENANT_RESPONSE"

# 2. Wait for tenant to be ready (optional)
sleep 10

# 3. Create deployment
DEPLOYMENT_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/deployments \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "data-team",
    "deploymentId": "my-airflow",
    "name": "My Airflow Instance",
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
  }')

echo "Deployment created: $DEPLOYMENT_RESPONSE"

# 4. Get deployment ID from response
DEPLOYMENT_ID=$(echo $DEPLOYMENT_RESPONSE | jq -r '.deploymentId')

# 5. Check deployment status
while true; do
  STATUS=$(curl -s http://localhost:8080/api/v1/deployments/$DEPLOYMENT_ID | jq -r '.status')
  echo "Status: $STATUS"

  if [ "$STATUS" = "RUNNING" ]; then
    echo "Deployment is ready!"
    break
  elif [ "$STATUS" = "FAILED" ]; then
    echo "Deployment failed!"
    exit 1
  fi

  sleep 30
done

# 6. Get Airflow UI URL
WEBSERVER_URL=$(curl -s http://localhost:8080/api/v1/deployments/$DEPLOYMENT_ID | jq -r '.webserverUrl')
echo "Airflow UI: $WEBSERVER_URL"
```

## Best Practices

### 1. Choosing the Right Deployment Option

**Use Kubernetes for:**
- Production workloads requiring high availability
- Large-scale deployments (>5,000 tasks/day)
- Multi-cloud requirements
- Complex networking requirements
- When you have Kubernetes expertise

**Use ECS for:**
- Test and staging environments
- Small-medium production workloads (100-5,000 tasks/day)
- AWS-native deployments
- Cost-conscious production
- When you want serverless benefits

**Use EC2 for:**
- Development and testing
- Proof of concepts
- Small workloads (<500 tasks/day)
- Budget-constrained projects
- Quick setup and teardown

### 2. Tenant Organization

- Create one tenant per team or environment
- Use descriptive tenant IDs (lowercase, hyphens)
- Examples: `data-engineering`, `ml-team`, `analytics-prod`

### 3. Deployment Sizing

**Small workloads (< 100 tasks/day):**
```
Executor: LOCAL or CELERY
Min Workers: 1
Max Workers: 2
Scheduler: 500-1000m CPU, 1-2Gi memory
Worker: 500m CPU, 1Gi memory
```

**Medium workloads (100-1000 tasks/day):**
```
Executor: CELERY
Min Workers: 2
Max Workers: 5
Scheduler: 1000m CPU, 2Gi memory
Worker: 1000m CPU, 2Gi memory
```

**Large workloads (> 1000 tasks/day):**
```
Executor: CELERY or CELERY_KUBERNETES
Min Workers: 3
Max Workers: 10+
Scheduler: 2000m CPU, 4Gi memory
Worker: 2000m CPU, 4Gi memory
```

### 4. Autoscaling Configuration

- Set `minWorkers` to handle baseline load
- Set `maxWorkers` based on infrastructure capacity
- Monitor queue depth and adjust as needed
- Consider cost vs. performance tradeoffs

**By deployment option:**
- **Kubernetes:** KEDA scales based on queue depth
- **ECS:** Auto-scales based on CPU/memory (70% and 80% targets)
- **EC2:** Manual scaling via Docker Compose

### 5. Naming Conventions

**Tenants:**
- Use lowercase with hyphens
- Example: `data-engineering`, `ml-team`, `analytics`

**Deployments:**
- Include environment and purpose
- Examples: `prod-etl`, `staging-ml`, `dev-analytics`

### 6. Resource Management

- Right-size resources based on actual workload
- Monitor resource usage regularly
- Use resource quotas for cost control (Kubernetes)
- Clean up unused deployments
- For EC2, choose appropriate instance type

### 7. Security

- Use custom domains with TLS certificates
- Rotate Airflow credentials regularly
- Use Kubernetes secrets / AWS Secrets Manager for sensitive data
- Implement network policies for isolation
- Enable Airflow authentication (LDAP, OAuth2, RBAC)
- For EC2, restrict SSH access and use SSM instead

### 8. High Availability

**Kubernetes:**
- Run multiple scheduler replicas (Airflow 2.x+)
- Use external PostgreSQL (RDS, Cloud SQL)
- Use external Redis (ElastiCache, MemoryStore)
- Configure pod disruption budgets
- Enable persistent volumes

**ECS:**
- Deploy tasks across multiple AZs
- Use Application Load Balancer for webserver
- Consider migrating to RDS for production (instead of containerized PostgreSQL)
- Enable EFS backups

**EC2:**
- Not recommended for HA requirements
- Consider ECS or Kubernetes for HA needs
- Use multi-AZ EBS volumes if available
- Implement regular backups

### 9. Backup and Recovery

**Kubernetes:**
- Backup Airflow metadata database regularly
- Use Velero for cluster-level backups
- Version control DAGs in Git with git-sync

**ECS:**
- Use AWS Backup for EFS snapshots
- Run pg_dump backups to S3
- Backup frequency: daily recommended

**EC2:**
- Create EBS snapshots regularly
- Run pg_dump backups to S3
- Consider AMI snapshots for full instance backup

**All Options:**
- Version control DAGs in Git repositories
- Document deployment configurations
- Test recovery procedures regularly

### 10. Monitoring

- Set up alerts for deployment failures
- Monitor task success rates in Airflow
- Track resource utilization (CPU, memory, disk)
- Monitor worker autoscaling behavior
- Set up CloudWatch alarms (for AWS deployments)
- Use Prometheus + Grafana (for Kubernetes)

### 11. Cost Optimization

**Kubernetes:**
- Use node auto-scaling
- Right-size pod resources
- Use spot instances for workers (if supported)
- Implement resource quotas per tenant

**ECS:**
- Use Fargate Spot for non-critical workloads (up to 70% savings)
- Enable EFS lifecycle policies (transition to IA after 30 days)
- Share ALB across multiple tenants
- Reduce CloudWatch Logs retention period

**EC2:**
- Use smaller instance types for dev/test
- Stop instances when not in use
- Use Spot Instances for significant savings
- Consider Reserved Instances for long-term

### 12. Upgrades

- Test upgrades in non-production first
- Review Airflow upgrade notes and breaking changes
- Backup metadata database before upgrading
- Use blue-green deployment strategy when possible
- Plan for downtime during major version upgrades

## Getting Help

- **Documentation:** Review docs in `docs/` directory
  - [Setup Guide](SETUP.md)
  - [Kubernetes Architecture](ARCHITECTURE.md)
  - [ECS Architecture](ARCHITECTURE_ECS.md)
  - [EC2 Architecture](ARCHITECTURE_EC2.md)
- **Logs:** Check component logs for errors (see "Viewing Logs" section)
- **API Docs:** Use Swagger UI for API reference
- **Community:** Join Apache Airflow Slack and community forums
- **Support:** Contact platform administrators

## Next Steps

- Explore Apache Airflow documentation: https://airflow.apache.org/docs/
- Learn about DAG development and best practices
- Set up monitoring and alerting
- Integrate with your CI/CD pipeline
- Configure authentication and authorization
- Set up automated backups
- Plan your scaling strategy

## Additional Resources

- [Apache Airflow Documentation](https://airflow.apache.org/docs/)
- [Apache Airflow Best Practices](https://airflow.apache.org/docs/apache-airflow/stable/best-practices.html)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [AWS ECS Documentation](https://docs.aws.amazon.com/ecs/)
- [AWS EC2 Documentation](https://docs.aws.amazon.com/ec2/)
- [Docker Compose Documentation](https://docs.docker.com/compose/)

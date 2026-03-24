# Local Testing Guide

This guide provides comprehensive instructions for testing the Managed Airflow Platform locally on your development machine.

## Table of Contents

- [Overview](#overview)
- [Why Local Testing?](#why-local-testing)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Detailed Setup](#detailed-setup)
- [Creating Your First Deployment](#creating-your-first-deployment)
- [Managing Deployments](#managing-deployments)
- [Working with DAGs](#working-with-dags)
- [Monitoring and Debugging](#monitoring-and-debugging)
- [Advanced Configuration](#advanced-configuration)
- [Troubleshooting](#troubleshooting)
- [Best Practices](#best-practices)
- [Cleaning Up](#cleaning-up)

## Overview

Local deployment mode allows you to run the complete Managed Airflow Platform on your development machine using Docker Compose. This is the fastest and simplest way to:

- Test platform features
- Develop and debug new functionality
- Experiment with configurations
- Demo the platform without cloud resources
- Learn how the platform works

### Architecture

In local mode:

```
┌─────────────────────────────────────────────────────────────┐
│                    Your Local Machine                       │
│                                                             │
│  ┌─────────────────────┐       ┌────────────────────────┐ │
│  │  Control Plane      │       │  ~/airflow-deployments │ │
│  │  (Spring Boot)      │       │                        │ │
│  │  Port: 8080         │◄─────►│  tenant1/              │ │
│  │                     │       │    deployment1/        │ │
│  │  - REST API         │       │      docker-compose.yml│ │
│  │  - H2 Database      │       │      (Airflow stack)   │ │
│  │  - Swagger UI       │       │    deployment2/        │ │
│  └─────────────────────┘       │      docker-compose.yml│ │
│                                 └────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

Each deployment runs as an isolated Docker Compose stack with:
- PostgreSQL (Airflow metadata)
- Redis (if using Celery executor)
- Airflow Webserver (unique port per deployment)
- Airflow Scheduler
- Airflow Workers (if using Celery)
- Flower (if using Celery)

## Why Local Testing?

### Advantages

✅ **No Cloud Costs**: Runs entirely on your machine
✅ **Fast Setup**: Ready in minutes, not hours
✅ **No Cloud Credentials**: No AWS/GCP/Azure accounts needed
✅ **Easy Debugging**: Direct access to logs and containers
✅ **Quick Iteration**: Rapid development and testing cycles
✅ **Offline Development**: Works without internet connection
✅ **Resource Isolation**: Multiple deployments side-by-side

### Limitations

⚠️ **Limited Resources**: Constrained by local machine capabilities
⚠️ **No High Availability**: Single instance of each component
⚠️ **No Autoscaling**: Manual scaling only
⚠️ **No Production Features**: No cloud integrations, backups, or monitoring
⚠️ **Data Persistence**: Data lives in local directories

### When to Use Local Mode

| Use Case | Local Mode | Cloud Mode |
|----------|------------|------------|
| Feature development | ✅ Ideal | ❌ Overkill |
| Integration testing | ✅ Good | ✅ Better |
| Learning/Training | ✅ Perfect | ❌ Too complex |
| Demos/POCs | ✅ Great | ⚠️ Depends |
| Production | ❌ No | ✅ Required |
| Load testing | ❌ Limited | ✅ Recommended |

## Prerequisites

### Required Software

1. **Docker** (20.10+)
   - [Docker Desktop for Mac](https://docs.docker.com/desktop/install/mac-install/)
   - [Docker Desktop for Windows](https://docs.docker.com/desktop/install/windows-install/)
   - [Docker Engine for Linux](https://docs.docker.com/engine/install/)

2. **Docker Compose** (2.0+)
   - Included with Docker Desktop
   - [Standalone installation](https://docs.docker.com/compose/install/)

3. **Java** (17+)
   - [OpenJDK 17](https://adoptium.net/)
   - [Oracle JDK 17](https://www.oracle.com/java/technologies/downloads/)

4. **Maven** (3.8+) *(Optional - can use wrapper)*
   - [Apache Maven](https://maven.apache.org/download.cgi)

5. **Node.js** (16+) and **npm** *(Optional - for Web UI)*
   - [Node.js Downloads](https://nodejs.org/)
   - npm is included with Node.js

### System Requirements

| Resource | Minimum | Recommended |
|----------|---------|-------------|
| CPU | 2 cores | 4+ cores |
| RAM | 8 GB | 16+ GB |
| Disk Space | 10 GB | 20+ GB |
| Docker Memory | 4 GB | 8+ GB |

### Verify Prerequisites

Run the setup script to check your environment:

```bash
cd infrastructure/local
./setup-local.sh
```

This script will:
- ✅ Check Docker and Docker Compose installation
- ✅ Verify Docker daemon is running
- ✅ Check Java version
- ✅ Verify available system resources
- ✅ Create the base deployment directory

## Quick Start

Get up and running in 5 minutes:

```bash
# 1. Clone the repository
git clone <repository-url>
cd managed-airflow-platform

# 2. Run setup script
cd infrastructure/local
./setup-local.sh

# 3. Start the control plane
cd ../../control-plane
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 4. (Optional) Start the frontend UI
cd ../frontend
npm install
npm start

# 5. Open Web UI or Swagger UI
open http://localhost:3000          # Web UI (recommended)
open http://localhost:8080/swagger-ui.html  # Swagger UI

# 6. Create a tenant and deployment (see next section)
```

## Detailed Setup

### Step 1: Configure Docker Resources

Ensure Docker has sufficient resources allocated:

#### Docker Desktop (Mac/Windows)

1. Open Docker Desktop
2. Go to **Settings** > **Resources**
3. Allocate at least:
   - **CPUs**: 4
   - **Memory**: 8 GB
   - **Disk**: 20 GB
4. Click **Apply & Restart**

#### Docker Engine (Linux)

Docker on Linux uses all available resources by default. No configuration needed.

### Step 2: Build the Control Plane

```bash
cd control-plane

# Option 1: Using Maven
mvn clean install

# Option 2: Using Maven wrapper
./mvnw clean install
```

This will:
- Download dependencies
- Compile the code
- Run tests
- Create the JAR file

### Step 3: Start the Control Plane

```bash
# Using Maven
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Or using the JAR file
java -jar target/control-plane-*.jar --spring.profiles.active=local
```

You should see:
```
Started ManagedAirflowControlPlaneApplication in X.XXX seconds
```

### Step 4: Verify the Control Plane

Open your browser and navigate to:

- **Web UI**: http://localhost:3000 (if frontend is running)
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **H2 Console**: http://localhost:8080/h2-console
  - JDBC URL: `jdbc:h2:mem:controlplane`
  - Username: `sa`
  - Password: *(leave empty)*

### Step 5: Start the Frontend (Optional)

For a user-friendly web interface, you can start the React frontend:

```bash
# Navigate to frontend directory
cd frontend

# Install dependencies (first time only)
npm install

# Start the development server
npm start
```

The frontend will open automatically at http://localhost:3000 and provides:
- Visual dashboard for tenants and deployments
- Easy deployment creation with forms
- One-click access to Airflow UI
- Deployment status monitoring
- Resource management interface

## Creating Your First Deployment

You can create deployments using three methods:
1. **Web UI** (Recommended - easiest)
2. **Swagger UI** (Interactive API documentation)
3. **curl/API** (Command-line/programmatic)

### Step 1: Create a Tenant

#### Option A: Using Web UI (Recommended)

1. Navigate to http://localhost:3000
2. Click on **Tenants** in the navigation
3. Click **Create Tenant** button
4. Fill in the form:
   - Tenant ID: `my-company`
   - Name: `My Company`
   - Email: `admin@mycompany.com`
5. Click **Submit**

#### Option B: Using curl

```bash
curl -X POST http://localhost:8080/api/v1/tenants \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "my-company",
    "name": "My Company",
    "email": "admin@mycompany.com"
  }'
```

#### Option C: Using Swagger UI

1. Navigate to http://localhost:8080/swagger-ui.html
2. Find `POST /api/v1/tenants`
3. Click **Try it out**
4. Enter the tenant details
5. Click **Execute**

### Step 2: Create a Deployment

#### Option A: Using Web UI (Recommended)

1. Navigate to http://localhost:3000
2. Click on **Deployments** in the navigation
3. Click **Create Deployment** button
4. Fill in the form:
   - **Tenant**: Select `my-company` from dropdown
   - **Deployment Name**: `my-airflow-dev`
   - **Description**: (optional) `Development Airflow instance`
   - **Airflow Version**: `3.1.8` (default)
   - **Executor Type**: `LOCAL` (recommended for local testing)
   - **Worker Autoscaling**: Min: `1`, Max: `3`
   - **Resource allocations**: Use default values or customize
5. Click **OK**

The deployment will start creating, and you'll see the status change from `DEPLOYING` to `RUNNING`.

6. Once running, click the **Open** button next to your deployment to access the Airflow UI directly

#### Option B: Using curl

```bash
curl -X POST http://localhost:8080/api/v1/deployments \
  -H "Content-Type: application/json" \
  -d '{
    "name": "my-airflow-dev",
    "tenantId": "my-company",
    "airflowVersion": "3.1.8",
    "executorType": "LOCAL",
    "webserverCpu": "500",
    "webserverMemory": "1024",
    "schedulerCpu": "500",
    "schedulerMemory": "1024",
    "workerCpu": "500",
    "workerMemory": "1024",
    "minWorkers": 1,
    "maxWorkers": 3
  }'
```

**Response:**
```json
{
  "deploymentId": "my-airflow-dev",
  "tenantId": "my-company",
  "status": "DEPLOYING",
  "airflowVersion": "3.1.8",
  "webserverUrl": "http://localhost:8093"
}
```

The deployment process will:
1. Create tenant directories if they don't exist
2. Create deployment directory
3. Generate `docker-compose.yml`
4. Pull Docker images (first time only)
5. Start all containers
6. Initialize Airflow database
7. Create admin user

### Step 3: Monitor Deployment Progress

Check deployment status:

```bash
curl http://localhost:8080/api/v1/deployments/my-airflow-dev
```

Or watch the logs:

```bash
# Find the deployment directory
cd ~/airflow-deployments/my-company/my-airflow-dev

# Watch logs
docker-compose logs -f
```

Wait for all services to be healthy (2-5 minutes).

### Step 4: Access Airflow UI

Once the deployment is running, access the Airflow webserver:

#### Option A: Using Web UI (Easiest)

1. Navigate to http://localhost:3000/deployments
2. Find your deployment `my-airflow-dev`
3. Click the **Open** button (with link icon)
4. The Airflow UI will open in a new tab

#### Option B: Using curl/Browser

```bash
# Get the webserver URL
curl http://localhost:8080/api/v1/deployments/my-airflow-dev | jq .webserverUrl

# Open in browser
open <webserver-url>
```

**Default credentials:**
- Username: `admin`
- Password: `admin`

## Managing Deployments

### List All Deployments

**Using Web UI:**
1. Navigate to http://localhost:3000
2. Click on **Deployments** in the navigation
3. View all deployments with their status, version, and resource information

**Using API:**
```bash
curl http://localhost:8080/api/v1/deployments
```

### Get Deployment Details

**Using Web UI:**
1. Navigate to http://localhost:3000/deployments
2. Click on any deployment to view full details

**Using API:**
```bash
curl http://localhost:8080/api/v1/deployments/{deployment-id}
```

### Access Airflow UI

**Using Web UI:**
1. Navigate to http://localhost:3000/deployments
2. Click the **Open** button (link icon) next to the deployment
3. The Airflow webserver will open in a new tab

### Scale Workers (Celery Executor Only)

**Using API:**
```bash
curl -X POST http://localhost:8080/api/v1/deployments/{deployment-id}/scale \
  -H "Content-Type: application/json" \
  -d '{
    "minWorkers": 3,
    "maxWorkers": 5
  }'
```

### Upgrade Deployment

**Using API:**
```bash
curl -X PUT http://localhost:8080/api/v1/deployments/{deployment-id} \
  -H "Content-Type: application/json" \
  -d '{
    "airflowVersion": "3.1.8",
    "webserverMemory": "2048"
  }'
```

### Delete Deployment

**Using Web UI:**
1. Navigate to http://localhost:3000/deployments
2. Click the **Delete** button next to the deployment
3. Confirm the deletion

**Using API:**
```bash
curl -X DELETE http://localhost:8080/api/v1/deployments/{deployment-id}
```

This will:
1. Stop all containers
2. Remove volumes
3. Delete the deployment directory

## Working with DAGs

### Upload DAGs

DAGs are stored in the deployment directory:

```bash
# Navigate to the DAGs directory
cd ~/airflow-deployments/{tenant-id}/{deployment-id}/dags

# Copy your DAG files
cp /path/to/your/dag.py .

# Or create a new DAG
cat > example_dag.py << 'EOF'
from datetime import datetime, timedelta
from airflow import DAG
from airflow.operators.bash import BashOperator

default_args = {
    'owner': 'airflow',
    'depends_on_past': False,
    'start_date': datetime(2024, 1, 1),
    'email_on_failure': False,
    'email_on_retry': False,
    'retries': 1,
    'retry_delay': timedelta(minutes=5),
}

dag = DAG(
    'example_local_dag',
    default_args=default_args,
    description='A simple example DAG for local testing',
    schedule_interval=timedelta(days=1),
)

t1 = BashOperator(
    task_id='print_date',
    bash_command='date',
    dag=dag,
)

t2 = BashOperator(
    task_id='sleep',
    bash_command='sleep 5',
    dag=dag,
)

t3 = BashOperator(
    task_id='print_hello',
    bash_command='echo "Hello from local Airflow!"',
    dag=dag,
)

t1 >> t2 >> t3
EOF
```

Airflow will automatically detect the new DAG within 30 seconds (default DAG scan interval).

### View DAG Logs

```bash
cd ~/airflow-deployments/{tenant-id}/{deployment-id}
docker-compose logs -f airflow-scheduler
```

## Monitoring and Debugging

### View Container Status

```bash
cd ~/airflow-deployments/{tenant-id}/{deployment-id}
docker-compose ps
```

### View Logs

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f airflow-webserver
docker-compose logs -f airflow-scheduler
docker-compose logs -f airflow-worker
```

### Access Container Shell

```bash
docker-compose exec airflow-webserver bash
```

### Check Resource Usage

```bash
docker stats
```

### Restart Services

```bash
# Restart all services
docker-compose restart

# Restart specific service
docker-compose restart airflow-webserver
```

### Flower (Celery Executor Only)

Monitor Celery workers using Flower:

```bash
# Get Flower URL (port 5555 + offset)
curl http://localhost:8080/api/v1/deployments/{deployment-id} | jq .flowerUrl

# Open in browser
open <flower-url>
```

## Advanced Configuration

### Custom Airflow Configuration

Edit the generated `docker-compose.yml` to add custom Airflow configurations:

```yaml
environment:
  AIRFLOW__CORE__DEFAULT_TIMEZONE: America/New_York
  AIRFLOW__CORE__DEFAULT_UI_TIMEZONE: America/New_York
  AIRFLOW__WEBSERVER__EXPOSE_CONFIG: 'true'
```

Then restart:
```bash
docker-compose up -d
```

### Custom Python Packages

Add packages to the Airflow containers:

```bash
# Access the container
docker-compose exec airflow-webserver bash

# Install packages
pip install pandas numpy scikit-learn

# Restart to apply
docker-compose restart airflow-scheduler airflow-worker
```

For permanent installation, create a custom Dockerfile:

```dockerfile
FROM apache/airflow:3.1.8
RUN pip install --no-cache-dir pandas numpy scikit-learn
```

### Using Different Executors

The platform supports multiple executors:

**LocalExecutor** (Default for local):
- Simple, single-machine execution
- No Redis required
- Good for development

**CeleryExecutor**:
- Distributed task execution
- Requires Redis
- Better for testing worker scaling

```json
{
  "executorType": "CELERY",
  "minWorkers": 2,
  "maxWorkers": 5
}
```

### Multiple Deployments

Run multiple deployments simultaneously:

```bash
# Create multiple deployments for the same tenant
curl -X POST http://localhost:8080/api/v1/deployments -d '{
  "deploymentId": "dev-airflow-1",
  "tenantId": "my-company",
  ...
}'

curl -X POST http://localhost:8080/api/v1/deployments -d '{
  "deploymentId": "dev-airflow-2",
  "tenantId": "my-company",
  ...
}'
```

Each deployment gets unique ports automatically.

## Troubleshooting

### Common Issues

#### 1. Port Conflicts

**Symptom:** `Bind for 0.0.0.0:8080 failed: port is already allocated`

**Solution:**
```bash
# Check what's using the port
lsof -i :8080

# Stop the conflicting service or change deployment ID
```

#### 2. Docker Daemon Not Running

**Symptom:** `Cannot connect to the Docker daemon`

**Solution:**
```bash
# macOS/Windows
open -a Docker

# Linux
sudo systemctl start docker
```

#### 3. Out of Memory

**Symptom:** Containers crash or fail to start

**Solution:**
- Increase Docker memory allocation (Docker Desktop)
- Reduce deployment resource requests
- Close other applications

#### 4. Airflow Init Fails

**Symptom:** `airflow-init` container exits with error

**Solution:**
```bash
# Check logs
cd ~/airflow-deployments/{tenant-id}/{deployment-id}
docker-compose logs airflow-init

# Reset and try again
docker-compose down -v
docker-compose up -d
```

#### 5. DAGs Not Appearing

**Symptom:** DAGs not showing in Airflow UI

**Solutions:**
- Verify DAG file is in correct directory
- Check DAG file for syntax errors:
  ```bash
  docker-compose exec airflow-scheduler python /opt/airflow/dags/your_dag.py
  ```
- Wait 30 seconds for DAG scan
- Check scheduler logs:
  ```bash
  docker-compose logs airflow-scheduler | grep -i error
  ```

### Debug Mode

Enable debug logging in the control plane:

```yaml
# application.yml (local profile)
logging:
  level:
    com.airflow.platform: DEBUG
```

Restart the control plane to see detailed logs.

### Clean State

If things get messy, reset everything:

```bash
# Stop control plane (Ctrl+C)

# Remove all deployments
cd ~/airflow-deployments
find . -name "docker-compose.yml" -execdir docker-compose down -v \;

# Remove all data
rm -rf ~/airflow-deployments/*

# Restart control plane
cd control-plane
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Best Practices

### Development Workflow

1. **Use LocalExecutor for Simple Testing**
   - Faster startup
   - Fewer resources
   - Easier debugging

2. **Use CeleryExecutor for Distributed Testing**
   - Test worker scaling
   - Test task distribution
   - More realistic production simulation

3. **One Tenant Per Project**
   - Easier to manage
   - Clear separation
   - Simpler cleanup

4. **Name Deployments Descriptively**
   - `project-dev`, `project-feature-x`
   - Easy to identify
   - Avoid conflicts

### Resource Management

1. **Monitor Resource Usage**
   ```bash
   docker stats
   ```

2. **Clean Up Unused Deployments**
   ```bash
   curl -X DELETE http://localhost:8080/api/v1/deployments/{old-deployment-id}
   ```

3. **Prune Docker Resources Regularly**
   ```bash
   docker system prune -a
   ```

### Testing Strategy

1. **Local → ECS → Production**
   - Develop locally
   - Test on ECS (cloud)
   - Deploy to production Kubernetes

2. **Use Same Airflow Version**
   - Match production version
   - Avoid compatibility issues

3. **Test with Sample Data**
   - Use smaller datasets locally
   - Verify logic before cloud testing

## Cleaning Up

### Remove a Single Deployment

Via API:
```bash
curl -X DELETE http://localhost:8080/api/v1/deployments/{deployment-id}
```

Manually:
```bash
cd ~/airflow-deployments/{tenant-id}/{deployment-id}
docker-compose down -v
cd ..
rm -rf {deployment-id}
```

### Remove a Tenant

Via API:
```bash
curl -X DELETE http://localhost:8080/api/v1/tenants/{tenant-id}
```

### Remove Everything

```bash
# Stop all deployments
cd ~/airflow-deployments
find . -name "docker-compose.yml" -execdir docker-compose down -v \;

# Delete all data
rm -rf ~/airflow-deployments/*

# Clean Docker resources
docker system prune -a -f
docker volume prune -f
```

## Next Steps

Now that you have local testing set up:

1. **Read the User Guide**: [USER_GUIDE.md](USER_GUIDE.md) - Learn all API operations
2. **Check Setup Guide**: [SETUP.md](SETUP.md) - Cloud deployment options
3. **Explore Examples**: Create different executor types and configurations
4. **Develop Features**: Start building your Airflow DAGs
5. **Move to Cloud**: When ready, deploy to ECS or Kubernetes

## References

- [Docker Documentation](https://docs.docker.com/)
- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [Apache Airflow Documentation](https://airflow.apache.org/docs/)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)

## Support

If you encounter issues:

1. Check this troubleshooting guide
2. Review logs: `docker-compose logs -f`
3. Check control plane logs
4. Open an issue on GitHub

---

Happy Testing! 🚀

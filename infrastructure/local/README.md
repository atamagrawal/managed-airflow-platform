# Local Infrastructure Setup

This directory contains setup scripts and configuration for running the Managed Airflow Platform locally on your machine.

## Overview

Local deployment is the simplest and fastest way to test the Managed Airflow Platform. It runs all components on your local machine using Docker Compose, without requiring any cloud infrastructure.

## Architecture

In local mode:
- **Control Plane**: Runs as a Spring Boot application on your machine
- **Airflow Deployments**: Each deployment runs in isolated Docker containers using Docker Compose
- **Tenant Isolation**: Each tenant gets a separate directory under `~/airflow-deployments/`
- **Port Allocation**: Webserver and Flower ports are dynamically allocated to avoid conflicts

## Prerequisites

### Required
- **Docker**: 20.10 or higher
- **Docker Compose**: 2.0 or higher (or Docker Compose plugin)
- **Java**: 17 or higher (for Spring Boot control plane)

### Optional
- **Maven**: 3.8+ (or use the Maven wrapper: `./mvnw`)

### System Resources
- **Memory**: At least 8GB RAM (4GB allocated to Docker recommended)
- **Disk Space**: ~5GB per deployment for Docker images and volumes
- **Ports**: Ensure ports 8080-8180 and 5555-5655 are available

## Quick Start

1. **Run the setup script**:
   ```bash
   ./setup-local.sh
   ```

   This script will:
   - Check all prerequisites
   - Verify Docker is running
   - Create the base deployment directory
   - Display system resource information

2. **Start the control plane**:
   ```bash
   cd ../../control-plane
   mvn spring-boot:run -Dspring-boot.run.profiles=local
   ```

3. **Access the API documentation**:
   - Swagger UI: http://localhost:8080/swagger-ui.html
   - H2 Console: http://localhost:8080/h2-console

4. **Create deployments using the REST API**

## Directory Structure

When you create deployments, the following directory structure is created:

```
~/airflow-deployments/
├── {tenant-id}/
│   ├── dags/                    # Shared DAGs directory for tenant
│   ├── logs/                    # Shared logs directory
│   ├── plugins/                 # Shared plugins directory
│   ├── data/                    # Data directory
│   ├── secrets/                 # Secrets stored as .properties files
│   └── {deployment-id}/         # Deployment-specific directory
│       ├── docker-compose.yml   # Generated Docker Compose file
│       ├── dags/                # Deployment DAGs (symlinked)
│       ├── logs/                # Deployment logs
│       └── plugins/             # Deployment plugins
```

## Port Allocation

To support multiple deployments simultaneously, ports are dynamically allocated:

- **Webserver**: `8080 + (deployment_id.hashCode() % 100)` (range: 8080-8180)
- **Flower** (Celery only): `5555 + (deployment_id.hashCode() % 100)` (range: 5555-5655)

## Configuration

The local profile is configured in `control-plane/src/main/resources/application.yml`:

```yaml
spring:
  config:
    activate:
      on-profile: local

deployment:
  provider: local

local:
  base-directory: ${user.home}/airflow-deployments
  docker-compose-timeout: 300
```

### Customization

You can override the default configuration by setting environment variables:

```bash
# Change base directory
export LOCAL_BASE_DIRECTORY=/custom/path

# Change timeout (in seconds)
export LOCAL_DOCKER_COMPOSE_TIMEOUT=600

# Run control plane
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Docker Compose Configuration

For each deployment, the control plane generates a `docker-compose.yml` file with:

- **PostgreSQL**: Database for Airflow metadata
- **Redis**: Message broker (for Celery executor only)
- **Airflow Webserver**: Web UI
- **Airflow Scheduler**: Task scheduler
- **Airflow Worker**: Task execution (for Celery executor only)
- **Flower**: Celery monitoring UI (for Celery executor only)

## Troubleshooting

### Docker daemon not running
```bash
# macOS
open -a Docker

# Linux
sudo systemctl start docker
```

### Port conflicts
If you encounter port conflicts, you can:
1. Stop other services using ports 8080-8180
2. Change the deployment ID to get a different port
3. Manually stop conflicting containers:
   ```bash
   docker ps
   docker stop <container-id>
   ```

### Out of disk space
Clean up old Docker resources:
```bash
# Remove stopped containers
docker container prune

# Remove unused images
docker image prune -a

# Remove unused volumes
docker volume prune
```

### Memory issues
Increase Docker memory allocation:
- **Docker Desktop**: Settings > Resources > Memory (recommend 4GB+)
- **Linux**: No limit by default

### View deployment logs
```bash
cd ~/airflow-deployments/{tenant-id}/{deployment-id}
docker-compose logs -f
```

## Limitations

Local deployment has some limitations compared to production deployments:

1. **No High Availability**: Single instance of each component
2. **Resource Constraints**: Limited by local machine resources
3. **No Autoscaling**: Worker scaling requires manual docker-compose commands
4. **No Cloud Integration**: Limited access to cloud services
5. **Persistence**: Data is lost if you delete the deployment directory

## Cleanup

To remove a deployment:
```bash
# Via API (recommended)
curl -X DELETE http://localhost:8080/api/v1/deployments/{deployment-id}

# Manual cleanup
cd ~/airflow-deployments/{tenant-id}/{deployment-id}
docker-compose down -v
cd ..
rm -rf {deployment-id}
```

To remove all deployments:
```bash
# Stop all running containers
cd ~/airflow-deployments
find . -name "docker-compose.yml" -execdir docker-compose down -v \;

# Delete all deployment data
rm -rf ~/airflow-deployments/*
```

## Next Steps

- Read the full documentation: [LOCAL_TESTING.md](../../docs/LOCAL_TESTING.md)
- View user guide: [USER_GUIDE.md](../../docs/USER_GUIDE.md)
- Check setup instructions: [SETUP.md](../../docs/SETUP.md)

## Support

For issues or questions:
- Check the documentation in `docs/`
- Review logs: `docker-compose logs -f`
- Open an issue on GitHub

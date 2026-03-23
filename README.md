# Managed Airflow Platform

A production-ready, multi-cloud, multi-tenant platform for deploying and managing Apache Airflow instances on Kubernetes, similar to Astronomer.

## Overview

The Managed Airflow Platform provides a complete solution for organizations to deploy, manage, and scale Apache Airflow across multiple cloud providers. Built with Java Spring Boot and React, it offers a powerful control plane for managing Airflow deployments with features like auto-scaling, multi-tenancy, and comprehensive monitoring.

## Key Features

- **Multi-Tenant Architecture** - Isolated namespaces for each tenant with dedicated Airflow deployments
- **Multi-Cloud Support** - Deploy on AWS EKS, Google GKE, Azure AKS, or on-premises Kubernetes
- **Auto-Scaling** - KEDA-based worker autoscaling based on queue depth
- **Control Plane UI** - React-based web interface for managing tenants and deployments
- **REST API** - Complete API for programmatic management
- **Kubernetes Native** - Leverages Kubernetes for orchestration and isolation
- **Helm Integration** - Uses official Apache Airflow Helm charts
- **Multiple Executor Support** - Local, Celery, Kubernetes, and hybrid executors
- **Resource Management** - Configurable CPU and memory allocations per component
- **Monitoring Ready** - Built-in health checks and metrics endpoints

## Architecture

```
┌──────────────┐
│   Users      │
└──────┬───────┘
       │
┌──────▼────────────────────────┐
│   Control Plane UI (React)    │
└──────┬────────────────────────┘
       │
┌──────▼────────────────────────┐
│  Control Plane API (Java)     │
│  - Tenant Management          │
│  - Deployment Management      │
│  - K8s Integration            │
└──────┬────────────────────────┘
       │
┌──────▼────────────────────────┐
│    Kubernetes Cluster         │
│  ┌──────────────────────────┐ │
│  │  Tenant Namespaces       │ │
│  │  - Airflow Webserver     │ │
│  │  - Airflow Scheduler     │ │
│  │  - Airflow Workers       │ │
│  │  - PostgreSQL            │ │
│  │  - Redis                 │ │
│  └──────────────────────────┘ │
│  ┌──────────────────────────┐ │
│  │  KEDA (Autoscaling)      │ │
│  │  Ingress Controller      │ │
│  └──────────────────────────┘ │
└───────────────────────────────┘
```

For detailed architecture, see [ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.8+
- Node.js 18+
- Kubernetes cluster (Minikube, EKS, GKE, AKS)
- kubectl
- Helm 3.x

### Local Development

1. **Clone the repository**

```bash
git clone <your-repo-url>
cd managed-airflow-platform
```

2. **Start the Control Plane API**

```bash
cd control-plane
mvn clean package
mvn spring-boot:run
```

The API will be available at `http://localhost:8080`

3. **Start the Frontend**

```bash
cd frontend
npm install
npm start
```

The UI will be available at `http://localhost:3000`

4. **Install KEDA**

```bash
helm repo add kedacore https://kedacore.github.io/charts
helm install keda kedacore/keda --namespace keda --create-namespace
```

5. **Add Airflow Helm Repository**

```bash
helm repo add apache-airflow https://airflow.apache.org
helm repo update
```

For detailed setup instructions, see [SETUP.md](docs/SETUP.md).

## Usage

### Creating a Tenant

Via UI:
1. Navigate to the Tenants page
2. Click "Create Tenant"
3. Fill in the details and submit

Via API:
```bash
curl -X POST http://localhost:8080/api/v1/tenants \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Data Engineering Team",
    "email": "data-eng@example.com",
    "organization": "Acme Corp",
    "cloudProvider": "AWS",
    "region": "us-east-1"
  }'
```

### Creating an Airflow Deployment

Via UI:
1. Navigate to the Deployments page
2. Click "Create Deployment"
3. Select tenant and configure Airflow settings
4. Submit to deploy

Via API:
```bash
curl -X POST http://localhost:8080/api/v1/deployments \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "data-engineering-team",
    "name": "Production ETL",
    "airflowVersion": "1.13.0",
    "executorType": "CELERY",
    "minWorkers": 1,
    "maxWorkers": 5,
    "schedulerCpu": "1000m",
    "schedulerMemory": "2Gi",
    "workerCpu": "1000m",
    "workerMemory": "2Gi"
  }'
```

For complete usage guide, see [USER_GUIDE.md](docs/USER_GUIDE.md).

## Project Structure

```
managed-airflow-platform/
├── control-plane/              # Spring Boot backend
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/airflow/platform/
│   │   │   │       ├── config/        # Configuration classes
│   │   │   │       ├── controller/    # REST controllers
│   │   │   │       ├── service/       # Business logic
│   │   │   │       ├── model/         # JPA entities
│   │   │   │       ├── repository/    # Data access
│   │   │   │       ├── dto/           # Request/Response DTOs
│   │   │   │       ├── exception/     # Exception handling
│   │   │   │       └── util/          # Utilities
│   │   │   └── resources/
│   │   │       └── application.yml
│   │   └── test/
│   └── pom.xml
│
├── frontend/                   # React frontend
│   ├── src/
│   │   ├── components/        # Reusable components
│   │   ├── pages/             # Page components
│   │   ├── services/          # API client
│   │   └── utils/             # Utilities
│   ├── public/
│   └── package.json
│
├── helm-charts/               # Helm charts
│   ├── airflow-deployment/   # Airflow deployment chart
│   │   ├── Chart.yaml
│   │   ├── values.yaml
│   │   └── templates/
│   └── platform-infrastructure/
│
├── kubernetes/                # K8s manifests
│   ├── namespace/            # Namespace definitions
│   ├── rbac/                 # RBAC configurations
│   ├── ingress/              # Ingress configurations
│   └── monitoring/           # Monitoring setup
│
├── docs/                     # Documentation
│   ├── ARCHITECTURE.md      # Architecture details
│   ├── SETUP.md             # Setup guide
│   └── USER_GUIDE.md        # User documentation
│
├── scripts/                  # Utility scripts
└── README.md                # This file
```

## Technology Stack

### Backend (Control Plane)
- **Java 17** - Programming language
- **Spring Boot 3.2** - Application framework
- **Spring Data JPA** - Data access
- **Spring Security** - Security framework
- **Kubernetes Java Client** - K8s integration
- **PostgreSQL** - Database (production)
- **H2** - Database (development)
- **Maven** - Build tool

### Frontend
- **React 18** - UI framework
- **React Router** - Routing
- **Ant Design** - UI component library
- **Axios** - HTTP client
- **Recharts** - Data visualization

### Infrastructure
- **Kubernetes** - Container orchestration
- **Helm** - Package manager
- **KEDA** - Event-driven autoscaling
- **Apache Airflow** - Workflow orchestration
- **PostgreSQL** - Airflow metadata database
- **Redis** - Celery message broker

## API Documentation

Once the control plane is running, access the interactive API documentation:

- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI Spec**: `http://localhost:8080/v3/api-docs`

## Configuration

### Control Plane Configuration

Edit `control-plane/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/airflow_control_plane
    username: postgres
    password: ${DB_PASSWORD}

helm:
  chart:
    path: ../helm-charts/airflow-deployment
  repo:
    name: apache-airflow
    url: https://airflow.apache.org
```

### Frontend Configuration

Create `frontend/.env.production`:

```bash
REACT_APP_API_URL=https://airflow-platform.example.com/api/v1
```

## Deployment

### Production Deployment

1. **Build Docker Images**

```bash
# Control Plane
cd control-plane
mvn clean package -DskipTests
docker build -t your-registry/managed-airflow-control-plane:latest .
docker push your-registry/managed-airflow-control-plane:latest

# Frontend
cd frontend
npm run build
docker build -t your-registry/managed-airflow-ui:latest .
docker push your-registry/managed-airflow-ui:latest
```

2. **Deploy to Kubernetes**

```bash
# Create namespace
kubectl apply -f kubernetes/namespace/control-plane-namespace.yaml

# Set up RBAC
kubectl apply -f kubernetes/rbac/control-plane-rbac.yaml

# Deploy control plane
kubectl apply -f kubernetes/control-plane-deployment.yaml
```

For complete deployment guide, see [SETUP.md](docs/SETUP.md).

## Monitoring

### Health Checks

```bash
# Control plane health
curl http://localhost:8080/actuator/health

# Metrics
curl http://localhost:8080/actuator/metrics
```

### Prometheus Integration

The control plane exposes metrics via Spring Boot Actuator at `/actuator/prometheus`.

### Grafana Dashboards

Import pre-built dashboards for:
- Platform overview
- Kubernetes cluster metrics
- Airflow task metrics
- KEDA autoscaling metrics

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
- Test deployment changes in a local cluster

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
kubectl apply -f kubernetes/test/

# Run integration tests
./scripts/run-integration-tests.sh
```

## Troubleshooting

### Common Issues

**Control Plane Won't Start:**
- Check database connectivity
- Verify Kubernetes access
- Check RBAC permissions

**Deployment Creation Fails:**
- Verify cluster resources
- Check Helm repository access
- Review control plane logs

**Workers Not Autoscaling:**
- Verify KEDA installation
- Check ScaledObject configuration
- Review queue metrics

For detailed troubleshooting, see [USER_GUIDE.md](docs/USER_GUIDE.md#monitoring-and-troubleshooting).

## Security Considerations

### Production Recommendations

1. **Authentication**
   - Implement JWT or OAuth2
   - Integrate with enterprise SSO

2. **Network Security**
   - Enable NetworkPolicies
   - Use TLS for all communications
   - Implement Pod Security Standards

3. **Secret Management**
   - Use external secret managers (Vault, AWS Secrets Manager)
   - Rotate credentials regularly
   - Encrypt secrets at rest

4. **RBAC**
   - Implement fine-grained RBAC
   - Follow principle of least privilege
   - Regular access reviews

## Roadmap

### Planned Features

- [ ] DAG management (Git integration)
- [ ] Cost tracking and billing
- [ ] Multi-cluster support
- [ ] Advanced monitoring dashboards
- [ ] Self-service tenant registration
- [ ] Plugin marketplace
- [ ] Compliance reporting (SOC 2, GDPR)
- [ ] CI/CD integration for DAGs
- [ ] Advanced authentication (SSO, MFA)
- [ ] Resource quota management

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

## Related Projects

- [Apache Airflow](https://github.com/apache/airflow)
- [Official Airflow Helm Chart](https://github.com/apache/airflow/tree/main/chart)
- [KEDA](https://github.com/kedacore/keda)

## Authors

- Your Name - Initial work

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for version history.

---

**Built with ❤️ for the data engineering community**

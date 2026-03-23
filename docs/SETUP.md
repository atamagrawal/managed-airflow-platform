# Managed Airflow Platform - Setup Guide

This guide provides step-by-step instructions for setting up and deploying the Managed Airflow Platform.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Local Development Setup](#local-development-setup)
3. [Kubernetes Cluster Setup](#kubernetes-cluster-setup)
4. [Production Deployment](#production-deployment)
5. [Configuration](#configuration)
6. [Troubleshooting](#troubleshooting)

## Prerequisites

### Required Tools

- **Java 17+** - For building the control plane
- **Maven 3.8+** - For building the control plane
- **Node.js 18+** - For building the frontend
- **npm or yarn** - For frontend dependencies
- **Docker** - For containerization
- **Kubernetes cluster** - K8s 1.24+
  - Local: Minikube, Kind, Docker Desktop
  - Cloud: EKS, GKE, AKS
- **kubectl** - Kubernetes CLI
- **Helm 3.x** - Package manager for Kubernetes
- **Git** - Version control

### Optional Tools

- **PostgreSQL** - For production database
- **Redis** - For Celery executor
- **KEDA** - For autoscaling (install in cluster)

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
mvn spring-boot:run
```

The API will be available at `http://localhost:8080`

**API Documentation (Swagger UI):**
- URL: http://localhost:8080/swagger-ui.html
- OpenAPI spec: http://localhost:8080/v3/api-docs

### 3. Build the Frontend

```bash
cd frontend

# Install dependencies
npm install

# Start development server
npm start
```

The UI will be available at `http://localhost:3000`

### 4. Configure Local Kubernetes Access

Ensure your `kubectl` is configured to access a Kubernetes cluster:

```bash
# Verify cluster access
kubectl cluster-info

# Create a namespace for testing
kubectl create namespace airflow-test
```

### 5. Install KEDA (Optional for Local Testing)

```bash
# Add KEDA Helm repository
helm repo add kedacore https://kedacore.github.io/charts
helm repo update

# Install KEDA
helm install keda kedacore/keda --namespace keda --create-namespace
```

### 6. Add Apache Airflow Helm Repository

```bash
# Add Airflow Helm repository
helm repo add apache-airflow https://airflow.apache.org
helm repo update
```

## Kubernetes Cluster Setup

### Option 1: Local Kubernetes with Minikube

```bash
# Start Minikube with sufficient resources
minikube start --cpus=4 --memory=8192 --disk-size=50g

# Enable ingress addon
minikube addons enable ingress

# Verify cluster
kubectl get nodes
```

### Option 2: AWS EKS

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

### Option 3: GCP GKE

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

### Option 4: Azure AKS

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

## Production Deployment

### Step 1: Install KEDA

```bash
helm repo add kedacore https://kedacore.github.io/charts
helm repo update
helm install keda kedacore/keda --namespace keda --create-namespace
```

### Step 2: Create Control Plane Namespace

```bash
kubectl apply -f kubernetes/namespace/control-plane-namespace.yaml
```

### Step 3: Set Up RBAC

```bash
kubectl apply -f kubernetes/rbac/control-plane-rbac.yaml
```

### Step 4: Deploy PostgreSQL (Production Database)

```bash
# Using Helm chart
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

helm install control-plane-db bitnami/postgresql \
  --namespace airflow-control-plane \
  --set auth.username=postgres \
  --set auth.password=<STRONG_PASSWORD> \
  --set auth.database=airflow_control_plane \
  --set primary.persistence.size=20Gi
```

**Or use managed database services:**
- AWS RDS
- GCP Cloud SQL
- Azure Database for PostgreSQL

### Step 5: Update Database Secret

```bash
# Edit the secret in kubernetes/control-plane-deployment.yaml
kubectl create secret generic control-plane-db-secret \
  --namespace airflow-control-plane \
  --from-literal=username=postgres \
  --from-literal=password=<STRONG_PASSWORD> \
  --from-literal=database=airflow_control_plane
```

### Step 6: Build and Push Docker Images

#### Build Control Plane Image

```bash
cd control-plane

# Build JAR
mvn clean package -DskipTests

# Build Docker image
docker build -t <your-registry>/managed-airflow-control-plane:latest .

# Push to registry
docker push <your-registry>/managed-airflow-control-plane:latest
```

**Sample Dockerfile for Control Plane:**

```dockerfile
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY target/managed-airflow-control-plane-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### Build Frontend Image

```bash
cd frontend

# Build production bundle
npm run build

# Build Docker image
docker build -t <your-registry>/managed-airflow-ui:latest .

# Push to registry
docker push <your-registry>/managed-airflow-ui:latest
```

**Sample Dockerfile for Frontend:**

```dockerfile
FROM node:18-alpine AS build

WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/build /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

**Sample nginx.conf:**

```nginx
server {
    listen 80;
    server_name localhost;
    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api {
        proxy_pass http://airflow-control-plane;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### Step 7: Update Deployment Manifests

Edit `kubernetes/control-plane-deployment.yaml`:
- Update image references to your registry
- Configure database connection settings
- Adjust resource limits as needed

### Step 8: Deploy Control Plane

```bash
kubectl apply -f kubernetes/control-plane-deployment.yaml
```

### Step 9: Verify Deployment

```bash
# Check pods
kubectl get pods -n airflow-control-plane

# Check services
kubectl get svc -n airflow-control-plane

# Check logs
kubectl logs -n airflow-control-plane -l app=managed-airflow-platform
```

### Step 10: Set Up Ingress (Optional)

```bash
# Install NGINX Ingress Controller
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update

helm install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx \
  --create-namespace

# Apply ingress configuration
kubectl apply -f kubernetes/ingress/control-plane-ingress.yaml
```

**Sample Ingress Configuration:**

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: control-plane-ingress
  namespace: airflow-control-plane
  annotations:
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
spec:
  ingressClassName: nginx
  tls:
    - hosts:
        - airflow-platform.example.com
      secretName: control-plane-tls
  rules:
    - host: airflow-platform.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: airflow-control-plane
                port:
                  number: 80
```

### Step 11: Install Cert-Manager (for TLS)

```bash
# Install cert-manager
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.13.0/cert-manager.yaml

# Create Let's Encrypt ClusterIssuer
cat <<EOF | kubectl apply -f -
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: admin@example.com
    privateKeySecretRef:
      name: letsencrypt-prod
    solvers:
    - http01:
        ingress:
          class: nginx
EOF
```

## Configuration

### Control Plane Configuration

Edit `control-plane/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://<DB_HOST>:5432/airflow_control_plane
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

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

### Frontend Configuration

Create `frontend/.env.production`:

```bash
REACT_APP_API_URL=https://airflow-platform.example.com/api/v1
```

### Environment Variables

**Control Plane:**
- `SPRING_PROFILES_ACTIVE` - Active profile (dev, prod)
- `DB_PASSWORD` - Database password
- `DB_USERNAME` - Database username
- `DB_HOST` - Database host

**Kubernetes:**
- Uses in-cluster config when deployed in K8s
- Uses `~/.kube/config` for local development

## Post-Deployment Verification

### 1. Health Check

```bash
# Check control plane health
curl http://airflow-platform.example.com/actuator/health

# Expected response:
# {"status":"UP"}
```

### 2. API Access

```bash
# List tenants (should return empty array initially)
curl http://airflow-platform.example.com/api/v1/tenants

# Check API docs
open http://airflow-platform.example.com/swagger-ui.html
```

### 3. Create Test Tenant

```bash
curl -X POST http://airflow-platform.example.com/api/v1/tenants \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Tenant",
    "email": "test@example.com",
    "organization": "Test Org",
    "cloudProvider": "AWS",
    "clusterName": "test-cluster",
    "region": "us-east-1"
  }'
```

### 4. Verify Namespace Creation

```bash
# Check if tenant namespace was created
kubectl get namespaces | grep airflow-
```

## Troubleshooting

### Common Issues

#### 1. Control Plane Pod Not Starting

```bash
# Check pod status
kubectl describe pod -n airflow-control-plane -l app=managed-airflow-platform

# Check logs
kubectl logs -n airflow-control-plane -l app=managed-airflow-platform --tail=100
```

**Common causes:**
- Database connection issues
- Kubernetes API access denied
- Missing RBAC permissions

#### 2. Deployment Creation Fails

```bash
# Check control plane logs
kubectl logs -n airflow-control-plane -l app=managed-airflow-platform | grep ERROR

# Check Helm release
helm list -n <tenant-namespace>

# Check Helm installation status
helm status <release-name> -n <tenant-namespace>
```

**Common causes:**
- Insufficient cluster resources
- Helm chart not found
- Invalid configuration values

#### 3. KEDA Not Scaling Workers

```bash
# Check KEDA operator logs
kubectl logs -n keda -l app=keda-operator

# Check ScaledObject status
kubectl get scaledobject -n <tenant-namespace>
kubectl describe scaledobject <name> -n <tenant-namespace>
```

**Common causes:**
- KEDA not installed
- Invalid trigger configuration
- Database connection issues for queue metrics

#### 4. Ingress Not Working

```bash
# Check ingress status
kubectl get ingress -n airflow-control-plane

# Check ingress controller logs
kubectl logs -n ingress-nginx -l app.kubernetes.io/name=ingress-nginx

# Test from within cluster
kubectl run -it --rm debug --image=curlimages/curl --restart=Never -- \
  curl http://airflow-control-plane.airflow-control-plane.svc.cluster.local
```

### Debug Mode

Enable debug logging in `application.yml`:

```yaml
logging:
  level:
    com.airflow.platform: DEBUG
    io.kubernetes: DEBUG
```

### Database Connection Issues

```bash
# Test database connectivity from control plane pod
kubectl exec -it -n airflow-control-plane <pod-name> -- \
  psql -h <DB_HOST> -U postgres -d airflow_control_plane
```

### Cleanup

```bash
# Delete all tenant namespaces
kubectl delete namespace -l managed-by=airflow-control-plane

# Delete control plane
kubectl delete -f kubernetes/control-plane-deployment.yaml
kubectl delete namespace airflow-control-plane

# Delete KEDA
helm uninstall keda -n keda
kubectl delete namespace keda

# Delete ingress controller
helm uninstall ingress-nginx -n ingress-nginx
```

## Backup and Restore

### Backup Control Plane Database

```bash
# PostgreSQL backup
kubectl exec -n airflow-control-plane control-plane-db-postgresql-0 -- \
  pg_dump -U postgres airflow_control_plane > backup.sql

# Or use managed database backup features
```

### Restore Control Plane Database

```bash
# Restore from backup
kubectl exec -i -n airflow-control-plane control-plane-db-postgresql-0 -- \
  psql -U postgres airflow_control_plane < backup.sql
```

## Monitoring Setup

### Install Prometheus and Grafana

```bash
# Add Prometheus Helm repository
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

# Install kube-prometheus-stack
helm install monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace \
  --set grafana.adminPassword=admin123

# Access Grafana
kubectl port-forward -n monitoring svc/monitoring-grafana 3000:80
```

### Import Dashboards

Access Grafana at `http://localhost:3000` and import:
- Kubernetes cluster dashboard
- Airflow metrics dashboard
- KEDA metrics dashboard

## Upgrading

### Upgrade Control Plane

```bash
# Build new image
cd control-plane
mvn clean package -DskipTests
docker build -t <your-registry>/managed-airflow-control-plane:<new-version> .
docker push <your-registry>/managed-airflow-control-plane:<new-version>

# Update deployment
kubectl set image deployment/airflow-control-plane \
  control-plane=<your-registry>/managed-airflow-control-plane:<new-version> \
  -n airflow-control-plane

# Verify rollout
kubectl rollout status deployment/airflow-control-plane -n airflow-control-plane
```

### Rollback

```bash
# Rollback to previous version
kubectl rollout undo deployment/airflow-control-plane -n airflow-control-plane

# Check rollout history
kubectl rollout history deployment/airflow-control-plane -n airflow-control-plane
```

## Next Steps

- Review the [User Guide](USER_GUIDE.md) for platform usage
- Review the [Architecture Documentation](ARCHITECTURE.md) for design details
- Set up monitoring and alerting
- Configure automated backups
- Implement CI/CD pipelines for control plane updates
- Set up multi-cluster support (if needed)

## Support

For issues and questions:
- Check logs: `kubectl logs -n airflow-control-plane -l app=managed-airflow-platform`
- Review Kubernetes events: `kubectl get events -n airflow-control-plane`
- Open an issue on GitHub

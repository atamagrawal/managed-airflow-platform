#!/bin/bash

# Deployment script for Managed Airflow Platform to Kubernetes
# This script deploys the platform to a Kubernetes cluster

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
NAMESPACE="airflow-control-plane"
REGISTRY="${DOCKER_REGISTRY:-your-registry}"
VERSION="${VERSION:-latest}"

echo "================================"
echo "Deploying Managed Airflow Platform"
echo "================================"

# Check if kubectl is installed
command -v kubectl >/dev/null 2>&1 || { echo -e "${RED}kubectl is not installed. Please install kubectl.${NC}" >&2; exit 1; }

# Check if helm is installed
command -v helm >/dev/null 2>&1 || { echo -e "${RED}Helm is not installed. Please install Helm.${NC}" >&2; exit 1; }

# Check cluster connectivity
echo -e "${YELLOW}Checking Kubernetes cluster connectivity...${NC}"
kubectl cluster-info > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Connected to Kubernetes cluster${NC}"
else
    echo -e "${RED}✗ Cannot connect to Kubernetes cluster${NC}"
    exit 1
fi

# Get script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Step 1: Install KEDA
echo ""
echo -e "${YELLOW}Step 1: Installing KEDA...${NC}"
if kubectl get namespace keda > /dev/null 2>&1; then
    echo -e "${BLUE}KEDA namespace already exists, skipping...${NC}"
else
    helm repo add kedacore https://kedacore.github.io/charts
    helm repo update
    helm install keda kedacore/keda --namespace keda --create-namespace
    echo -e "${GREEN}✓ KEDA installed${NC}"
fi

# Step 2: Add Airflow Helm repository
echo ""
echo -e "${YELLOW}Step 2: Adding Airflow Helm repository...${NC}"
helm repo add apache-airflow https://airflow.apache.org
helm repo update
echo -e "${GREEN}✓ Airflow Helm repository added${NC}"

# Step 3: Create namespace
echo ""
echo -e "${YELLOW}Step 3: Creating namespace...${NC}"
kubectl apply -f "$PROJECT_ROOT/kubernetes/namespace/control-plane-namespace.yaml"
echo -e "${GREEN}✓ Namespace created${NC}"

# Step 4: Set up RBAC
echo ""
echo -e "${YELLOW}Step 4: Setting up RBAC...${NC}"
kubectl apply -f "$PROJECT_ROOT/kubernetes/rbac/control-plane-rbac.yaml"
echo -e "${GREEN}✓ RBAC configured${NC}"

# Step 5: Deploy PostgreSQL (optional, comment out if using external DB)
echo ""
echo -e "${YELLOW}Step 5: Deploying PostgreSQL...${NC}"
read -p "Do you want to deploy PostgreSQL for the control plane? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    helm repo add bitnami https://charts.bitnami.com/bitnami
    helm repo update

    helm install control-plane-db bitnami/postgresql \
        --namespace $NAMESPACE \
        --set auth.username=postgres \
        --set auth.password=postgres123 \
        --set auth.database=airflow_control_plane \
        --set primary.persistence.size=20Gi

    echo -e "${GREEN}✓ PostgreSQL deployed${NC}"
else
    echo -e "${BLUE}Skipping PostgreSQL deployment${NC}"
fi

# Step 6: Update deployment manifests with image registry
echo ""
echo -e "${YELLOW}Step 6: Updating deployment manifests...${NC}"
if [ "$REGISTRY" != "your-registry" ]; then
    sed -i.bak "s|your-registry|$REGISTRY|g" "$PROJECT_ROOT/kubernetes/control-plane-deployment.yaml"
    sed -i.bak "s|:latest|:$VERSION|g" "$PROJECT_ROOT/kubernetes/control-plane-deployment.yaml"
    echo -e "${GREEN}✓ Deployment manifests updated${NC}"
else
    echo -e "${YELLOW}⚠ Using default registry. Set DOCKER_REGISTRY environment variable to use custom registry.${NC}"
fi

# Step 7: Deploy control plane
echo ""
echo -e "${YELLOW}Step 7: Deploying control plane...${NC}"
kubectl apply -f "$PROJECT_ROOT/kubernetes/control-plane-deployment.yaml"
echo -e "${GREEN}✓ Control plane deployed${NC}"

# Step 8: Wait for deployment
echo ""
echo -e "${YELLOW}Step 8: Waiting for deployment to be ready...${NC}"
kubectl wait --for=condition=available --timeout=300s \
    deployment/airflow-control-plane -n $NAMESPACE

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Deployment is ready${NC}"
else
    echo -e "${RED}✗ Deployment failed to become ready${NC}"
    exit 1
fi

# Step 9: Display deployment information
echo ""
echo -e "${GREEN}================================${NC}"
echo -e "${GREEN}Deployment completed successfully!${NC}"
echo -e "${GREEN}================================${NC}"
echo ""
echo "Deployment Information:"
echo "  Namespace: $NAMESPACE"
echo ""
echo "Access the control plane:"
echo "  Port-forward: kubectl port-forward -n $NAMESPACE svc/airflow-control-plane 8080:80"
echo "  Then access: http://localhost:8080"
echo ""
echo "Check deployment status:"
echo "  kubectl get pods -n $NAMESPACE"
echo "  kubectl get svc -n $NAMESPACE"
echo ""
echo "View logs:"
echo "  kubectl logs -n $NAMESPACE -l app=managed-airflow-platform -f"
echo ""
echo "Next steps:"
echo "  1. Configure ingress for external access"
echo "  2. Set up monitoring and alerting"
echo "  3. Review security settings"
echo "  4. Create your first tenant and deployment"

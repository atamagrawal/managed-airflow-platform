#!/bin/bash

# Setup script for local Airflow deployment
# This script checks prerequisites and sets up the local environment

set -e

echo "=========================================="
echo "Managed Airflow Platform - Local Setup"
echo "=========================================="
echo ""

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to print success message
print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

# Function to print error message
print_error() {
    echo -e "${RED}✗${NC} $1"
}

# Function to print warning message
print_warning() {
    echo -e "${YELLOW}!${NC} $1"
}

# Check prerequisites
echo "Checking prerequisites..."
echo ""

PREREQUISITES_MET=true

# Check Docker
if command_exists docker; then
    DOCKER_VERSION=$(docker --version | cut -d ' ' -f3 | cut -d ',' -f1)
    print_success "Docker is installed (version $DOCKER_VERSION)"

    # Check if Docker daemon is running
    if docker info >/dev/null 2>&1; then
        print_success "Docker daemon is running"
    else
        print_error "Docker daemon is not running. Please start Docker."
        PREREQUISITES_MET=false
    fi
else
    print_error "Docker is not installed"
    echo "   Please install Docker from: https://docs.docker.com/get-docker/"
    PREREQUISITES_MET=false
fi

# Check Docker Compose
if command_exists docker-compose; then
    COMPOSE_VERSION=$(docker-compose --version | cut -d ' ' -f4 | cut -d ',' -f1)
    print_success "Docker Compose is installed (version $COMPOSE_VERSION)"
elif docker compose version >/dev/null 2>&1; then
    COMPOSE_VERSION=$(docker compose version --short)
    print_success "Docker Compose (plugin) is installed (version $COMPOSE_VERSION)"
else
    print_error "Docker Compose is not installed"
    echo "   Please install Docker Compose from: https://docs.docker.com/compose/install/"
    PREREQUISITES_MET=false
fi

# Check Java (for control plane)
if command_exists java; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d '"' -f2)
    print_success "Java is installed (version $JAVA_VERSION)"

    # Check if Java version is 17 or higher
    JAVA_MAJOR_VERSION=$(echo "$JAVA_VERSION" | cut -d '.' -f1)
    if [ "$JAVA_MAJOR_VERSION" -ge 17 ]; then
        print_success "Java version is 17 or higher"
    else
        print_warning "Java version should be 17 or higher for Spring Boot 3.x"
    fi
else
    print_error "Java is not installed"
    echo "   Please install Java 17 or higher"
    PREREQUISITES_MET=false
fi

# Check Maven (optional, can use wrapper)
if command_exists mvn; then
    MVN_VERSION=$(mvn --version | head -n 1 | cut -d ' ' -f3)
    print_success "Maven is installed (version $MVN_VERSION)"
else
    print_warning "Maven is not installed (you can use Maven wrapper: ./mvnw)"
fi

echo ""

# Check system resources
echo "Checking system resources..."
echo ""

# Check available disk space
if command_exists df; then
    AVAILABLE_SPACE=$(df -h . | awk 'NR==2 {print $4}')
    print_success "Available disk space: $AVAILABLE_SPACE"
    echo "   Note: Each Airflow deployment requires ~2-5GB of disk space"
fi

# Check available memory (macOS)
if [[ "$OSTYPE" == "darwin"* ]]; then
    TOTAL_MEM=$(sysctl -n hw.memsize | awk '{print int($1/1024/1024/1024)"GB"}')
    print_success "Total system memory: $TOTAL_MEM"
    echo "   Note: Ensure Docker has at least 4GB of memory allocated"
    echo "   Configure in: Docker Desktop > Settings > Resources"
fi

# Check available memory (Linux)
if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    TOTAL_MEM=$(free -h | awk '/^Mem:/ {print $2}')
    print_success "Total system memory: $TOTAL_MEM"
    echo "   Note: Each Airflow deployment requires ~2-4GB of memory"
fi

echo ""

# Create base directory for deployments
BASE_DIR="${HOME}/airflow-deployments"
if [ ! -d "$BASE_DIR" ]; then
    echo "Creating base directory for Airflow deployments..."
    mkdir -p "$BASE_DIR"
    print_success "Created directory: $BASE_DIR"
else
    print_success "Base directory exists: $BASE_DIR"
fi

echo ""

# Summary
echo "=========================================="
if [ "$PREREQUISITES_MET" = true ]; then
    print_success "All prerequisites are met!"
    echo ""
    echo "Next steps:"
    echo "  1. Start the control plane:"
    echo "     cd control-plane"
    echo "     mvn spring-boot:run -Dspring-boot.run.profiles=local"
    echo ""
    echo "  2. Access the API documentation:"
    echo "     http://localhost:8080/swagger-ui.html"
    echo ""
    echo "  3. Create a tenant and deployment using the API"
    echo ""
    echo "For detailed instructions, see: docs/LOCAL_TESTING.md"
else
    print_error "Some prerequisites are missing. Please install them and run this script again."
    exit 1
fi
echo "=========================================="

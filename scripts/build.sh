#!/bin/bash

# Build script for Managed Airflow Platform
# This script builds both the control plane and frontend

set -e

echo "================================"
echo "Building Managed Airflow Platform"
echo "================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if commands exist
command -v mvn >/dev/null 2>&1 || { echo -e "${RED}Maven is not installed. Please install Maven.${NC}" >&2; exit 1; }
command -v npm >/dev/null 2>&1 || { echo -e "${RED}Node.js/npm is not installed. Please install Node.js.${NC}" >&2; exit 1; }

# Get script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo -e "${YELLOW}Building Control Plane...${NC}"
cd "$PROJECT_ROOT/control-plane"
mvn clean package -DskipTests

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Control Plane built successfully${NC}"
else
    echo -e "${RED}✗ Control Plane build failed${NC}"
    exit 1
fi

echo ""
echo -e "${YELLOW}Building Frontend...${NC}"
cd "$PROJECT_ROOT/frontend"

# Install dependencies if node_modules doesn't exist
if [ ! -d "node_modules" ]; then
    echo "Installing frontend dependencies..."
    npm install
fi

npm run build

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Frontend built successfully${NC}"
else
    echo -e "${RED}✗ Frontend build failed${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}================================${NC}"
echo -e "${GREEN}Build completed successfully!${NC}"
echo -e "${GREEN}================================${NC}"
echo ""
echo "Next steps:"
echo "  1. Start the control plane: cd control-plane && mvn spring-boot:run"
echo "  2. Start the frontend: cd frontend && npm start"
echo "  OR"
echo "  Use Docker Compose: docker-compose up"

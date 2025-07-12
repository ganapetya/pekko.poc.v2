#!/bin/bash

# Build script for Pekko Cluster POC

set -e

echo "============================================"
echo "Building Pekko Cluster Case Resolution POC"
echo "============================================"

# Clean and build
echo "1. Cleaning and building with Maven..."
mvn clean package -DskipTests

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "Error: Docker is not running. Please start Docker and try again."
    exit 1
fi

# Build Docker image
echo "2. Building Docker image..."
docker build -t pekko-cluster-poc .

echo "3. Build completed successfully!"
echo ""
echo "To start the cluster, run:"
echo "  docker-compose up"
echo ""
echo "To start individual services:"
echo "  docker-compose up seed-node"
echo "  docker-compose up node-1"
echo "  docker-compose up node-2"
echo ""
echo "API endpoints will be available at:"
echo "  http://localhost:8080 (seed-node)"
echo "  http://localhost:8081 (node-1)"
echo "  http://localhost:8082 (node-2)"
echo ""
echo "Management endpoints:"
echo "  http://localhost:8558 (seed-node)"
echo "  http://localhost:8559 (node-1)"
echo "  http://localhost:8560 (node-2)" 
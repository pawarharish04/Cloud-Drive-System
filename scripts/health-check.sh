#!/bin/bash
# Health Check Script for Cloud Drive System

set -e

echo "========================================="
echo "Cloud Drive System Health Check"
echo "========================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to check service health
check_service() {
    local service_name=$1
    local port=$2
    local endpoint=${3:-/actuator/health}
    
    echo -n "Checking $service_name... "
    
    if response=$(curl -s -f http://localhost:$port$endpoint 2>&1); then
        if echo "$response" | grep -q '"status":"UP"'; then
            echo -e "${GREEN}✓ UP${NC}"
            return 0
        else
            echo -e "${YELLOW}⚠ DEGRADED${NC}"
            echo "  Response: $response"
            return 1
        fi
    else
        echo -e "${RED}✗ DOWN${NC}"
        return 1
    fi
}

# Function to check Docker container
check_container() {
    local container_name=$1
    
    echo -n "Checking container $container_name... "
    
    if docker ps --filter "name=$container_name" --filter "status=running" | grep -q "$container_name"; then
        echo -e "${GREEN}✓ RUNNING${NC}"
        return 0
    else
        echo -e "${RED}✗ NOT RUNNING${NC}"
        return 1
    fi
}

# Check if Docker Compose is running
echo "1. Docker Containers"
echo "-------------------"
check_container "cloud-drive-postgres"
check_container "metadata-service"
check_container "file-service"
check_container "api-gateway" || echo -e "${YELLOW}  (Optional)${NC}"
check_container "auth-service" || echo -e "${YELLOW}  (Optional)${NC}"
echo ""

# Check service health endpoints
echo "2. Service Health Endpoints"
echo "---------------------------"
check_service "PostgreSQL" 5432 "" || true  # PostgreSQL doesn't have HTTP endpoint
check_service "Metadata Service" 8083
check_service "File Service" 8082
check_service "API Gateway" 8080 || echo -e "${YELLOW}  (Optional)${NC}"
echo ""

# Check database connectivity
echo "3. Database Connectivity"
echo "------------------------"
echo -n "Checking PostgreSQL connection... "
if docker exec cloud-drive-postgres psql -U clouduser -d metadatadb -c "\dt" > /dev/null 2>&1; then
    echo -e "${GREEN}✓ CONNECTED${NC}"
    
    # Count records
    file_count=$(docker exec cloud-drive-postgres psql -U clouduser -d metadatadb -t -c "SELECT COUNT(*) FROM file_metadata;" 2>/dev/null | tr -d ' ')
    chunk_count=$(docker exec cloud-drive-postgres psql -U clouduser -d metadatadb -t -c "SELECT COUNT(*) FROM chunk_metadata;" 2>/dev/null | tr -d ' ')
    
    echo "  Files: $file_count"
    echo "  Chunks: $chunk_count"
else
    echo -e "${RED}✗ FAILED${NC}"
fi
echo ""

# Check internal networking
echo "4. Internal Networking"
echo "----------------------"
echo -n "Checking file-service → metadata-service... "
if docker exec file-service ping -c 1 -W 2 metadata-service > /dev/null 2>&1; then
    echo -e "${GREEN}✓ OK${NC}"
else
    echo -e "${RED}✗ FAILED${NC}"
fi

echo -n "Checking file-service → postgres... "
if docker exec file-service ping -c 1 -W 2 postgres > /dev/null 2>&1; then
    echo -e "${GREEN}✓ OK${NC}"
else
    echo -e "${RED}✗ FAILED${NC}"
fi
echo ""

# Check logs for errors
echo "5. Recent Errors"
echo "----------------"
echo -n "Checking file-service logs... "
error_count=$(docker logs file-service --tail 100 2>&1 | grep -i "ERROR" | wc -l)
if [ "$error_count" -eq 0 ]; then
    echo -e "${GREEN}✓ No errors${NC}"
else
    echo -e "${YELLOW}⚠ $error_count errors found${NC}"
    docker logs file-service --tail 100 2>&1 | grep -i "ERROR" | tail -3
fi

echo -n "Checking metadata-service logs... "
error_count=$(docker logs metadata-service --tail 100 2>&1 | grep -i "ERROR" | wc -l)
if [ "$error_count" -eq 0 ]; then
    echo -e "${GREEN}✓ No errors${NC}"
else
    echo -e "${YELLOW}⚠ $error_count errors found${NC}"
    docker logs metadata-service --tail 100 2>&1 | grep -i "ERROR" | tail -3
fi
echo ""

# Summary
echo "========================================="
echo "Health Check Complete"
echo "========================================="
echo ""
echo "Next steps:"
echo "  - Run E2E tests: see docs/Quick_Test_Guide.md"
echo "  - View logs: docker logs <service-name>"
echo "  - Restart services: docker-compose restart"
echo ""

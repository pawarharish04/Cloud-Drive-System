# Cloud Drive System

This project is a cloud storage system designed with a microservices architecture. It allows users to upload, manage, and share files securely.
## Folder Structure
- `api-gateway/`: Acts as the single entry point for all client requests.
- `auth-service/`: Manages user authentication and authorization.
- `file-service/`: Handles file storage and retrieval operations.
- `metadata-service/`: Stores metadata about files (e.g., name, size, owner).
- `notification-service/`: Sends notifications to users (optional).
- `docker-compose.yml`: Orchestrates the microservices using Docker.

## Getting Started

1.  Navigate to each service directory and follow the setup instructions (TBD).
2.  Run `docker-compose up --build` to start the entire system locally.

## Version History
- **v0.4**: Production-Grade Exception Handling & Reliability
- **v0.5**: Security Hardening (Encryption, Presigned URLs, Authorization)
- **v0.6**: Production Configuration & Docker Hardening
- **v0.7**: Automated Integration Testing (Testcontainers)

## Quick Start

### Prerequisites
- Docker & Docker Compose
- AWS Account with S3 bucket
- Java 17+ (for local development)

### Running with Docker
```bash
# 1. Copy environment template
cp .env.example .env

# 2. Edit .env with your AWS credentials and database settings

# 3. Start all services
docker-compose up --build

# 4. Access the API Gateway
curl http://localhost:8080/health
```

For detailed configuration options, see [`docs/Configuration_Guide.md`](docs/Configuration_Guide.md).

## Testing & Validation

### Quick Validation
```bash
# Run basic health checks
./scripts/health-check.sh

# Run E2E validation
# See docs/Quick_Test_Guide.md for detailed steps
```

### Automated Integration Tests
```bash
# Run all integration tests (requires Docker)
mvn test

# Run specific test class
mvn test -Dtest=MetadataServiceIntegrationTest

# Run specific test method
mvn test -Dtest=SecurityIntegrationTest#shouldBlockNonOwnerFromDownloading
```

**Test Coverage**:
- ✅ 30+ integration tests using Testcontainers
- ✅ Real PostgreSQL database (not H2)
- ✅ Full upload lifecycle validation
- ✅ Security and authorization tests
- ✅ Idempotency validation
- ✅ CI/CD ready (no external dependencies)

See [`docs/Testing_Strategy.md`](docs/Testing_Strategy.md) for detailed testing approach.

### Comprehensive Testing
- **E2E Validation**: [`docs/E2E_Validation_Checklist.md`](docs/E2E_Validation_Checklist.md)
- **Chaos Testing**: [`docs/Chaos_Testing_Scenarios.md`](docs/Chaos_Testing_Scenarios.md)
- **Quick Tests**: [`docs/Quick_Test_Guide.md`](docs/Quick_Test_Guide.md)

### Key Test Scenarios
- ✅ Multipart upload flow (initiate → chunk → complete)
- ✅ Secure download via presigned URLs
- ✅ Idempotency (duplicate chunk/complete requests)
- ✅ Crash recovery (service restart mid-upload)
- ✅ Security (cross-user access blocked)
- ✅ Data persistence (survives container restart)

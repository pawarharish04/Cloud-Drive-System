# Cloud Drive System

A cloud storage system built with a microservices architecture for securely uploading, managing, storing, and sharing files. The project is designed around modular services, Docker-based local development, AWS-backed storage, and integration testing with Testcontainers.

## Overview

Cloud Drive System provides a scalable file-management platform with the following goals:

- Secure file uploads and downloads
- User authentication and authorization
- Metadata tracking for stored files
- Optional notifications for file-related events
- Local development and deployment through Docker
- Production-oriented testing and validation

## Features

- Microservices-based design
- API gateway for centralized request routing
- Authentication and authorization service
- File upload and retrieval workflows
- Metadata persistence for file details
- Docker Compose orchestration
- Presigned URL support for secure access
- Encryption and security hardening
- Integration tests using Testcontainers
- Crash recovery and idempotency validation

## Repository Structure

- `api-gateway/` — Entry point for client requests and service routing
- `auth-service/` — Handles authentication and authorization
- `file-service/` — Manages file storage and retrieval
- `metadata-service/` — Stores file metadata such as name, size, and owner
- `notification-service/` — Sends user notifications when enabled
- `docker-compose.yml` — Runs the services together locally
- `docs/` — Documentation for configuration, testing, and validation
- `scripts/` — Utility scripts such as health checks

## Version History

- **v0.4** — Production-grade exception handling and reliability
- **v0.5** — Security hardening, including encryption, presigned URLs, and authorization
- **v0.6** — Production configuration and Docker hardening
- **v0.7** — Automated integration testing with Testcontainers

## Prerequisites

Before running the project, make sure you have:

- Docker
- Docker Compose
- Java 17 or newer
- AWS account with an S3 bucket
- Maven for running tests locally

## Quick Start

### 1. Clone the repository

```bash
git clone https://github.com/pawarharish04/Cloud-Drive-System.git
cd Cloud-Drive-System
```

### 2. Configure environment variables

```bash
cp .env.example .env
```

Update `.env` with your AWS credentials, database settings, and any service-specific configuration.

### 3. Start the services

```bash
docker-compose up --build
```

### 4. Verify the system

```bash
curl http://localhost:8080/health
```

## Configuration

For detailed configuration guidance, see:

- [`docs/Configuration_Guide.md`](docs/Configuration_Guide.md)

Typical configuration areas include:

- AWS credentials and bucket settings
- Database connection details
- Service ports and URLs
- Security-related environment variables

## Testing & Validation

### Quick validation

```bash
./scripts/health-check.sh
```

### Integration tests

```bash
mvn test
```

Run a specific test class:

```bash
mvn test -Dtest=MetadataServiceIntegrationTest
```

Run a specific test method:

```bash
mvn test -Dtest=SecurityIntegrationTest#shouldBlockNonOwnerFromDownloading
```

### Testing documentation

- [`docs/Testing_Strategy.md`](docs/Testing_Strategy.md)
- [`docs/E2E_Validation_Checklist.md`](docs/E2E_Validation_Checklist.md)
- [`docs/Chaos_Testing_Scenarios.md`](docs/Chaos_Testing_Scenarios.md)
- [`docs/Quick_Test_Guide.md`](docs/Quick_Test_Guide.md)

### What is covered

- Multipart upload flow: initiate → chunk → complete
- Secure download via presigned URLs
- Duplicate request/idempotency handling
- Crash recovery after service restart
- Security checks for cross-user access
- Data persistence after container restart
- PostgreSQL-backed integration testing
- CI/CD-friendly validation without external dependencies

## API and Service Notes

This system uses a gateway-driven architecture. In general:

- Clients communicate with the `api-gateway`
- Authentication is handled by `auth-service`
- File operations are handled by `file-service`
- Metadata is managed by `metadata-service`
- Notifications are handled by `notification-service` when enabled

Refer to the service-specific documentation in the repository for exact endpoints, request payloads, and runtime behavior.

## Troubleshooting

If you encounter issues:

- Confirm Docker and Docker Compose are running
- Ensure `.env` is populated correctly
- Verify your AWS bucket and credentials are valid
- Check service logs from `docker-compose`
- Run the health check script to narrow down failures

## Contributing

When contributing changes:

- Keep documentation in sync with code changes
- Add or update tests for new behavior
- Verify the full Docker and integration-test flow before merging

## License

Add the project license here if one applies.

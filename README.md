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

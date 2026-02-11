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

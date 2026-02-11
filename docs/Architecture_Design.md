# 🏗️ Cloud Drive System - Architecture Design

## 📌 System Overview

A production-grade, cloud-native distributed file storage system built with Java Spring Boot microservices, inspired by Google Drive.

---

## 🎯 Core Architecture

### Microservices Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Client Layer                             │
│                    (Web/Mobile/Desktop)                          │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                       API Gateway                                │
│              (Spring Cloud Gateway + JWT Filter)                 │
└─────┬──────────┬──────────┬──────────┬──────────┬──────────────┘
      │          │          │          │          │
      ▼          ▼          ▼          ▼          ▼
┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
│   Auth   │ │   File   │ │ Metadata │ │  Notif.  │ │  Future  │
│ Service  │ │ Service  │ │ Service  │ │ Service  │ │ Services │
└────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ └──────────┘
     │            │            │            │
     ▼            ▼            ▼            ▼
┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐
│   H2    │  │   S3    │  │   H2    │  │  Email  │
│   DB    │  │ Storage │  │   DB    │  │  SMTP   │
└─────────┘  └─────────┘  └─────────┘  └─────────┘
```

---

## 🔧 Service Responsibilities

### 1. API Gateway
**Port**: 8080  
**Technology**: Spring Cloud Gateway

**Responsibilities:**
- Single entry point for all client requests
- JWT token validation
- Request routing to appropriate microservices
- Rate limiting (future)
- Load balancing (future)

**Routes:**
- `/auth/**` → Auth Service
- `/files/**` → File Service
- `/metadata/**` → Metadata Service

---

### 2. Auth Service
**Port**: 8081  
**Technology**: Spring Boot + Spring Security + JWT

**Responsibilities:**
- User registration and login
- JWT token generation and validation
- Password encryption (BCrypt)
- User session management

**Key Components:**
- `AuthController` - REST endpoints
- `AuthService` - Business logic
- `JwtUtil` - Token generation/validation
- `UserRepository` - Database access
- `GlobalExceptionHandler` - Error handling

**Database Schema:**
```sql
users (
  id BIGINT PRIMARY KEY,
  username VARCHAR(255) UNIQUE,
  email VARCHAR(255) UNIQUE,
  password VARCHAR(255),  -- BCrypt hashed
  role VARCHAR(50)
)
```

---

### 3. File Service
**Port**: 8082  
**Technology**: Spring Boot + AWS S3 SDK

**Responsibilities:**
- File upload (chunked and direct)
- File download
- File deletion
- S3 multipart upload management
- Upload session tracking

**Key Components:**
- `FileController` - REST endpoints for file operations
- `ChunkUploadController` - REST endpoints for chunked uploads
- `FileService` - Business logic for file operations
- `ChunkUploadService` - Business logic for chunking
- `S3StorageService` - S3 operations wrapper
- `S3MultipartService` - S3 multipart upload wrapper
- `UploadSession` entity - Track in-progress uploads

**Upload Flow (Chunked):**
```
1. POST /files/initiate-upload
   → Returns uploadId, chunkSize

2. POST /files/upload-chunk (repeat for each chunk)
   → Upload chunk to S3
   → Store chunk metadata

3. POST /files/complete-upload
   → Finalize S3 multipart upload
   → Notify Metadata Service
   → Return file URL
```

---

### 4. Metadata Service
**Port**: 8083  
**Technology**: Spring Boot + JPA + H2

**Responsibilities:**
- Store file metadata (name, size, owner, upload date)
- Store chunk metadata (chunk number, ETag, size)
- Query files by user
- Track file versions (future)

**Key Components:**
- `MetadataController` - REST endpoints
- `MetadataService` - Business logic
- `FileMetadataRepository` - Database access
- `GlobalExceptionHandler` - Error handling

**Database Schema:**
```sql
file_metadata (
  id BIGINT PRIMARY KEY,
  file_name VARCHAR(255),
  file_type VARCHAR(100),
  size BIGINT,
  s3_key VARCHAR(500),
  owner VARCHAR(255),
  uploaded_at TIMESTAMP
)

chunk_metadata (
  id BIGINT PRIMARY KEY,
  file_id BIGINT,  -- FK to file_metadata
  chunk_number INT,
  etag VARCHAR(255),
  size BIGINT,
  uploaded_at TIMESTAMP
)
```

---

### 5. Notification Service
**Port**: 8084  
**Technology**: Spring Boot + Spring Mail

**Responsibilities:**
- Send email notifications
- File upload completion alerts
- Sharing notifications (future)
- Event-driven notifications (Kafka/RabbitMQ in future)

**Key Components:**
- `EmailService` - Email sending logic
- `FileEventConsumer` - Event consumer (future)

---

## 🔐 Security Architecture

### Authentication Flow

```
1. User → Auth Service: POST /auth/register
   → Create user account

2. User → Auth Service: POST /auth/login
   → Validate credentials
   → Generate JWT token
   → Return token to user

3. User → API Gateway: GET /files/my-files
   Header: Authorization: Bearer <JWT>
   → Gateway validates JWT
   → Routes to File Service
   → File Service processes request
```

### JWT Token Structure

```json
{
  "sub": "username",
  "iat": 1707667200,
  "exp": 1707753600,
  "roles": ["USER"]
}
```

---

## 📊 Data Flow

### File Upload Flow (Chunked)

```
┌────────┐                                                    ┌─────────┐
│ Client │                                                    │   S3    │
└───┬────┘                                                    └────▲────┘
    │                                                              │
    │ 1. POST /files/initiate-upload                              │
    ├──────────────────────────────────────────────────┐          │
    │                                                   ▼          │
    │                                            ┌──────────────┐  │
    │                                            │ File Service │  │
    │                                            └──────┬───────┘  │
    │ 2. Response: { uploadId, chunkSize }             │          │
    │◄─────────────────────────────────────────────────┘          │
    │                                                              │
    │ 3. POST /files/upload-chunk (chunk 1)                       │
    ├──────────────────────────────────────────────────┐          │
    │                                                   ▼          │
    │                                            ┌──────────────┐  │
    │                                            │ File Service │  │
    │                                            └──────┬───────┘  │
    │                                                   │          │
    │                                                   │ Upload   │
    │                                                   ├──────────┤
    │                                                              │
    │ 4. Response: { chunkNumber, etag }                          │
    │◄─────────────────────────────────────────────────┘          │
    │                                                              │
    │ (Repeat steps 3-4 for all chunks)                           │
    │                                                              │
    │ 5. POST /files/complete-upload                              │
    ├──────────────────────────────────────────────────┐          │
    │                                                   ▼          │
    │                                            ┌──────────────┐  │
    │                                            │ File Service │  │
    │                                            └──────┬───────┘  │
    │                                                   │          │
    │                                                   │ Complete │
    │                                                   ├──────────┤
    │                                                              │
    │                                                   │          │
    │                                                   ▼          │
    │                                            ┌──────────────┐  │
    │                                            │   Metadata   │  │
    │                                            │   Service    │  │
    │                                            └──────────────┘  │
    │                                                              │
    │ 6. Response: { fileId, fileUrl }                            │
    │◄─────────────────────────────────────────────────┘          │
    │                                                              │
```

---

## 🚀 Scaling Strategy

### Horizontal Scaling

**Stateless Services:**
- Auth Service: Scale based on authentication load
- File Service: Scale based on upload/download traffic
- Metadata Service: Scale based on query load

**Load Balancing:**
- API Gateway handles load distribution
- Round-robin or least-connections algorithm

### Vertical Scaling

**Database:**
- Migrate from H2 to PostgreSQL/MySQL for production
- Read replicas for metadata queries
- Connection pooling

**Storage:**
- S3 auto-scales infinitely
- Use CloudFront CDN for downloads (future)

### Caching Strategy (Future)

- Redis for upload session tracking
- Redis for frequently accessed metadata
- CDN for static file downloads

---

## 🛡️ Fault Tolerance

### Retry Mechanisms

- **Chunk Upload**: Retry failed chunks up to 3 times
- **S3 Operations**: Exponential backoff for transient failures
- **Service Communication**: Circuit breaker pattern (future)

### Cleanup Strategies

- **Abandoned Uploads**: Auto-delete after 24 hours
- **Failed Uploads**: Cleanup incomplete S3 multipart uploads
- **Orphaned Metadata**: Periodic cleanup jobs

---

## 📈 Future Enhancements

1. **Parallel Chunk Upload**: Upload multiple chunks simultaneously
2. **File Sharing**: Share files with other users
3. **File Versioning**: Track file history
4. **Deduplication**: Avoid storing duplicate files
5. **Compression**: Compress files before storage
6. **Encryption**: End-to-end encryption
7. **Event-Driven Architecture**: Kafka/RabbitMQ for async communication
8. **Monitoring**: Prometheus + Grafana
9. **Distributed Tracing**: Zipkin/Jaeger

---

**Document Version**: v1.0  
**Last Updated**: 2026-02-11  
**Author**: Cloud Drive System Team

# Configuration & Deployment Guide

## Configuration Philosophy

This system follows the **12-Factor App** methodology for configuration management:

1. **Strict Separation of Config from Code**: All environment-specific settings live in environment variables or profile-specific YAML files.
2. **Environment Parity**: Dev, Staging, and Prod run identical code with different configurations.
3. **No Secrets in Code**: AWS credentials, database passwords, and JWT secrets are injected at runtime.

## Configuration Hierarchy

```
application.yml (defaults)
  ↓
application-{profile}.yml (environment-specific overrides)
  ↓
Environment Variables (runtime injection)
```

## Environment Profiles

### Development (`dev`)
- **Database**: H2 in-memory (fast iteration)
- **Logging**: DEBUG level for all services
- **Limits**: Relaxed (1GB max file size)
- **S3**: Local bucket or dev bucket

### Production (`prod`)
- **Database**: PostgreSQL with connection pooling
- **Logging**: WARN/INFO only
- **Limits**: Strict (5GB max file size)
- **S3**: Production bucket with encryption enforced

## Docker Networking Model

```
┌─────────────────────────────────────────┐
│         Internet / Client               │
└──────────────────┬──────────────────────┘
                   │
                   ▼
         ┌─────────────────┐
         │  API Gateway    │ ← Only Public Port (8080)
         │   (Port 8080)   │
         └────────┬────────┘
                  │
    ┌─────────────┼─────────────┐
    │             │             │
    ▼             ▼             ▼
┌────────┐  ┌──────────┐  ┌──────────┐
│  Auth  │  │   File   │  │ Metadata │
│ Service│  │ Service  │  │ Service  │
└────┬───┘  └─────┬────┘  └─────┬────┘
     │            │              │
     └────────────┴──────────────┘
                  │
                  ▼
          ┌──────────────┐
          │  PostgreSQL  │
          └──────────────┘
```

**Key Points:**
- All services communicate via **service names** (e.g., `http://metadata-service:8083`).
- Only `api-gateway:8080` is exposed to the host.
- Internal network (`cloud-drive-network`) isolates services.

## Secret Management

### Local Development
1. Copy `.env.example` to `.env`
2. Fill in your AWS credentials and database passwords
3. Run `docker-compose up`

### Production (AWS EC2/ECS)
1. **Use IAM Roles**: Attach an IAM role to the EC2 instance or ECS task with S3 permissions.
2. **Remove Credentials**: Set `AWS_ACCESS_KEY` and `AWS_SECRET_KEY` to empty strings.
3. **Use AWS Secrets Manager** or **Parameter Store** for database passwords.

### Kubernetes
Use **Kubernetes Secrets** to inject environment variables:
```yaml
env:
  - name: S3_BUCKET
    valueFrom:
      secretKeyRef:
        name: cloud-drive-secrets
        key: s3-bucket
```

## Connection Pooling (HikariCP)

**Configuration Rationale:**
- **Max Pool Size**: 10 (dev), 20 (prod) — Balances throughput and resource usage.
- **Min Idle**: 2 (dev), 5 (prod) — Keeps warm connections ready.
- **Connection Timeout**: 30s — Fails fast if DB is unreachable.
- **Max Lifetime**: 30 minutes — Prevents stale connections.

## Logging Strategy

### What We Log
- **INFO**: Business events (upload initiated, file completed)
- **WARN**: Recoverable errors (retry attempts, access denied)
- **ERROR**: Critical failures (S3 down, DB unreachable)

### What We DON'T Log
- ❌ Presigned URLs (security risk)
- ❌ AWS credentials
- ❌ User passwords
- ❌ S3 Upload IDs in production

## Validation & Limits

| Limit | Dev | Prod |
|-------|-----|------|
| Max File Size | 1GB | 5GB |
| Chunk Size | 5MB | 10MB |
| Max Chunk Size | 100MB | 100MB |
| Presigned URL Expiry | 10 min | 10 min |

## Running the System

### Local Development
```bash
# 1. Copy environment template
cp .env.example .env

# 2. Edit .env with your AWS credentials

# 3. Start all services
docker-compose up --build

# 4. Access API Gateway
curl http://localhost:8080/health
```

### Production Deployment
```bash
# 1. Set environment variables in your deployment platform
export SPRING_PROFILE=prod
export S3_BUCKET=my-prod-bucket
# ... (use secrets manager for sensitive values)

# 2. Deploy with production profile
docker-compose -f docker-compose.prod.yml up -d
```

## Health Checks

All services expose `/actuator/health` for monitoring:
- **Metadata Service**: `http://metadata-service:8083/actuator/health`
- **File Service**: `http://file-service:8082/actuator/health`
- **Auth Service**: `http://auth-service:8081/actuator/health`

Docker Compose uses these for dependency orchestration.

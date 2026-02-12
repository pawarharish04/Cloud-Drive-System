# Quick Test Execution Guide

## Pre-Requisites
```bash
# 1. Environment setup
cp .env.example .env
# Edit .env with your AWS credentials

# 2. Clean start
docker-compose down -v
docker-compose up --build -d

# 3. Wait for all services to be healthy
sleep 30

# 4. Verify health
curl http://localhost:8083/actuator/health  # Metadata
curl http://localhost:8082/actuator/health  # File
curl http://localhost:8080/actuator/health  # Gateway
```

---

## Test 1: Happy Path (5 minutes)

```bash
# Register user
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username": "testuser", "email": "test@example.com", "password": "Pass123!"}'

# Login
JWT_TOKEN=$(curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "testuser", "password": "Pass123!"}' | jq -r '.token')

# Initiate upload
FILE_ID=$(curl -X POST http://localhost:8080/files/upload/initiate \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fileName": "test.txt", "fileSize": 10485760, "totalChunks": 2, "contentType": "text/plain"}' \
  | jq -r '.fileId')

# Create test chunks
dd if=/dev/urandom of=chunk1.bin bs=5M count=1
dd if=/dev/urandom of=chunk2.bin bs=5M count=1

# Upload chunks
curl -X POST http://localhost:8080/files/upload/chunk \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -F "fileId=$FILE_ID" \
  -F "chunkNumber=1" \
  -F "chunk=@chunk1.bin"

curl -X POST http://localhost:8080/files/upload/chunk \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -F "chunkNumber=2" \
  -F "chunk=@chunk2.bin"

# Complete upload
curl -X POST http://localhost:8080/files/upload/complete \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fileId": '$FILE_ID'}'

# Get download URL
DOWNLOAD_URL=$(curl -X GET http://localhost:8080/files/$FILE_ID/download \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "X-User-Id: testuser" | jq -r '.downloadUrl')

# Download file
curl -o downloaded.txt "$DOWNLOAD_URL"

# Verify
ls -lh downloaded.txt  # Should be ~10MB
```

**Expected**: All commands succeed, file downloads correctly.

---

## Test 2: Idempotency (2 minutes)

```bash
# Upload same chunk twice
curl -X POST http://localhost:8080/files/upload/chunk \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -F "fileId=$FILE_ID" \
  -F "chunkNumber=1" \
  -F "chunk=@chunk1.bin"

curl -X POST http://localhost:8080/files/upload/chunk \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -F "fileId=$FILE_ID" \
  -F "chunkNumber=1" \
  -F "chunk=@chunk1.bin"

# Check database
docker exec -it cloud-drive-postgres psql -U clouduser -d metadatadb \
  -c "SELECT chunk_number, COUNT(*) FROM chunk_metadata WHERE file_id=$FILE_ID GROUP BY chunk_number;"
```

**Expected**: Only 1 row for chunk 1, both uploads return 200 OK.

---

## Test 3: Security (3 minutes)

```bash
# Register attacker
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username": "attacker", "email": "bad@example.com", "password": "Pass123!"}'

ATTACKER_TOKEN=$(curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "attacker", "password": "Pass123!"}' | jq -r '.token')

# Try to access testuser's file
curl -X GET http://localhost:8080/files/$FILE_ID/download \
  -H "Authorization: Bearer $ATTACKER_TOKEN" \
  -H "X-User-Id: attacker"
```

**Expected**: 403 Forbidden, error message about unauthorized access.

---

## Test 4: Crash Recovery (5 minutes)

```bash
# Start new upload
FILE_ID2=$(curl -X POST http://localhost:8080/files/upload/initiate \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fileName": "crash-test.txt", "fileSize": 10485760, "totalChunks": 2, "contentType": "text/plain"}' \
  | jq -r '.fileId')

# Upload chunk 1
curl -X POST http://localhost:8080/files/upload/chunk \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -F "fileId=$FILE_ID2" \
  -F "chunkNumber=1" \
  -F "chunk=@chunk1.bin"

# Kill file-service
docker kill file-service

# Try to upload chunk 2 (will fail)
curl -X POST http://localhost:8080/files/upload/chunk \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -F "fileId=$FILE_ID2" \
  -F "chunkNumber=2" \
  -F "chunk=@chunk2.bin"

# Restart
docker-compose up -d file-service
sleep 15

# Retry chunk 2
curl -X POST http://localhost:8080/files/upload/chunk \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -F "fileId=$FILE_ID2" \
  -F "chunkNumber=2" \
  -F "chunk=@chunk2.bin"

# Complete
curl -X POST http://localhost:8080/files/upload/complete \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fileId": '$FILE_ID2'}'
```

**Expected**: Upload resumes successfully after restart.

---

## Test 5: Data Persistence (2 minutes)

```bash
# Restart all containers
docker-compose restart

# Wait
sleep 30

# Verify data still exists
curl -X GET http://localhost:8080/files/$FILE_ID \
  -H "Authorization: Bearer $JWT_TOKEN"

# Generate new download URL
curl -X GET http://localhost:8080/files/$FILE_ID/download \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "X-User-Id: testuser"
```

**Expected**: File metadata persists, new presigned URL generated.

---

## Test 6: Observability (1 minute)

```bash
# Check logs for sensitive data
docker logs file-service --tail 100 | grep -i "presigned"
docker logs file-service --tail 100 | grep -i "aws.*secret"

# Check structured logging
docker logs metadata-service --tail 50
```

**Expected**: 
- No presigned URLs in logs
- No AWS credentials in logs
- Consistent log format

---

## Test 7: Performance Sanity (3 minutes)

```bash
# Concurrent uploads
for i in {1..5}; do
  FILE_ID_PERF=$(curl -X POST http://localhost:8080/files/upload/initiate \
    -H "Authorization: Bearer $JWT_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"fileName": "perf-'$i'.txt", "fileSize": 5242880, "totalChunks": 1, "contentType": "text/plain"}' \
    | jq -r '.fileId')
  
  curl -X POST http://localhost:8080/files/upload/chunk \
    -H "Authorization: Bearer $JWT_TOKEN" \
    -F "fileId=$FILE_ID_PERF" \
    -F "chunkNumber=1" \
    -F "chunk=@chunk1.bin" &
done

wait

# Check for deadlocks
docker logs metadata-service --tail 100 | grep -i "deadlock"
```

**Expected**: All uploads succeed, no deadlocks.

---

## Cleanup

```bash
# Stop all containers
docker-compose down

# Remove volumes (fresh start)
docker-compose down -v

# Clean up test files
rm -f chunk1.bin chunk2.bin downloaded.txt
```

---

## Quick Validation Matrix

| Test | Expected Result | Pass/Fail |
|------|----------------|-----------|
| Happy Path | File uploaded and downloadable | ☐ |
| Idempotency | No duplicate chunks | ☐ |
| Security | 403 on cross-user access | ☐ |
| Crash Recovery | Upload resumes after restart | ☐ |
| Data Persistence | Data survives restart | ☐ |
| Observability | No sensitive data in logs | ☐ |
| Performance | No deadlocks under load | ☐ |

---

## Troubleshooting

**Services won't start**:
```bash
docker-compose logs
docker ps -a
```

**Database connection errors**:
```bash
docker exec -it cloud-drive-postgres psql -U clouduser -d metadatadb -c "\dt"
```

**S3 upload fails**:
```bash
# Verify AWS credentials
aws s3 ls s3://$S3_BUCKET
```

**Network issues**:
```bash
docker network inspect cloud-drive-network
docker exec -it file-service ping metadata-service
```

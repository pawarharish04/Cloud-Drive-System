# End-to-End Validation Checklist

## Pre-Flight Checks

### Environment Setup
- [ ] `.env` file created from `.env.example`
- [ ] AWS credentials configured (S3 bucket exists)
- [ ] S3 bucket is private (no public access)
- [ ] Docker and Docker Compose installed
- [ ] Ports 8080-8083, 5432 available

### Build & Start
```bash
# Clean start
docker-compose down -v
docker-compose up --build
```

**Expected Output**:
- [ ] PostgreSQL starts first (healthy in ~10s)
- [ ] Metadata Service starts (waits for DB health)
- [ ] File Service starts (waits for Metadata)
- [ ] Auth Service starts
- [ ] API Gateway starts last
- [ ] No error logs during startup

---

## Test Suite 1: Service Health Validation

### 1.1 Health Endpoint Check
```bash
# Test all health endpoints
curl http://localhost:8083/actuator/health  # Metadata Service
curl http://localhost:8082/actuator/health  # File Service
curl http://localhost:8081/actuator/health  # Auth Service (if implemented)
curl http://localhost:8080/actuator/health  # API Gateway (if implemented)
```

**Expected**:
- [ ] All return `{"status":"UP"}`
- [ ] Response time < 500ms

### 1.2 Database Connectivity
```bash
# Check PostgreSQL
docker exec -it cloud-drive-postgres psql -U clouduser -d metadatadb -c "\dt"
```

**Expected**:
- [ ] Tables: `file_metadata`, `chunk_metadata` exist
- [ ] No connection errors

### 1.3 Service Discovery
```bash
# Verify internal networking
docker exec -it file-service ping -c 2 metadata-service
docker exec -it file-service ping -c 2 postgres
```

**Expected**:
- [ ] Services can resolve each other by name
- [ ] No DNS failures

---

## Test Suite 2: Authentication Flow

### 2.1 User Registration
```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "SecurePass123!"
  }'
```

**Expected**:
- [ ] Status: 200 OK
- [ ] Response contains user ID

### 2.2 User Login
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "SecurePass123!"
  }'
```

**Expected**:
- [ ] Status: 200 OK
- [ ] Response contains JWT token
- [ ] Save token as `$JWT_TOKEN`

### 2.3 Token Validation
```bash
# Use token in subsequent requests
curl -X GET http://localhost:8080/files/metadata \
  -H "Authorization: Bearer $JWT_TOKEN"
```

**Expected**:
- [ ] Status: 200 OK (or 404 if no files)
- [ ] NOT 401 Unauthorized

---

## Test Suite 3: Multipart Upload Flow

### 3.1 Initiate Upload
```bash
curl -X POST http://localhost:8080/files/upload/initiate \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "fileName": "test-file.txt",
    "fileSize": 15728640,
    "totalChunks": 3,
    "contentType": "text/plain"
  }'
```

**Expected**:
- [ ] Status: 200 OK
- [ ] Response contains `fileId`, `uploadId`, `s3Key`
- [ ] Save `fileId` as `$FILE_ID`

**Validation**:
```bash
# Check database state
docker exec -it cloud-drive-postgres psql -U clouduser -d metadatadb \
  -c "SELECT id, file_name, status FROM file_metadata WHERE id=$FILE_ID;"
```
- [ ] Status: `PENDING` or `ACTIVE`

### 3.2 Upload Chunks
```bash
# Create test file
dd if=/dev/urandom of=test-chunk-1.bin bs=5M count=1

# Upload chunk 1
curl -X POST http://localhost:8080/files/upload/chunk \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -F "fileId=$FILE_ID" \
  -F "chunkNumber=1" \
  -F "chunk=@test-chunk-1.bin"

# Upload chunk 2
dd if=/dev/urandom of=test-chunk-2.bin bs=5M count=1
curl -X POST http://localhost:8080/files/upload/chunk \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -F "fileId=$FILE_ID" \
  -F "chunkNumber=2" \
  -F "chunk=@test-chunk-2.bin"

# Upload chunk 3
dd if=/dev/urandom of=test-chunk-3.bin bs=5M count=1
curl -X POST http://localhost:8080/files/upload/chunk \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -F "fileId=$FILE_ID" \
  -F "chunkNumber=3" \
  -F "chunk=@test-chunk-3.bin"
```

**Expected (per chunk)**:
- [ ] Status: 200 OK
- [ ] Response contains `chunkNumber`, `etag`
- [ ] Logs show "Uploaded part X for uploadId"

**Validation**:
```bash
# Check chunk metadata
docker exec -it cloud-drive-postgres psql -U clouduser -d metadatadb \
  -c "SELECT chunk_number, etag FROM chunk_metadata WHERE file_id=$FILE_ID ORDER BY chunk_number;"
```
- [ ] 3 rows returned
- [ ] ETags are non-null

### 3.3 Complete Upload
```bash
curl -X POST http://localhost:8080/files/upload/complete \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "fileId": '$FILE_ID'
  }'
```

**Expected**:
- [ ] Status: 200 OK
- [ ] Response contains `fileUrl` (S3 path, not public URL)
- [ ] Logs show "Completed multipart upload"

**Validation**:
```bash
# Check final state
docker exec -it cloud-drive-postgres psql -U clouduser -d metadatadb \
  -c "SELECT status FROM file_metadata WHERE id=$FILE_ID;"
```
- [ ] Status: `COMPLETED`

### 3.4 S3 Verification
```bash
# Verify file exists in S3
aws s3 ls s3://$S3_BUCKET/uploads/ --recursive | grep test-file.txt
```
- [ ] File exists in S3
- [ ] Size matches expected (~15MB)

---

## Test Suite 4: Secure Download Flow

### 4.1 Generate Presigned URL
```bash
curl -X GET http://localhost:8080/files/$FILE_ID/download \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "X-User-Id: testuser"
```

**Expected**:
- [ ] Status: 200 OK
- [ ] Response contains `downloadUrl`
- [ ] URL contains `X-Amz-Signature` (presigned)
- [ ] URL expires in 10 minutes
- [ ] Save URL as `$DOWNLOAD_URL`

### 4.2 Download File
```bash
curl -o downloaded-file.txt "$DOWNLOAD_URL"
```

**Expected**:
- [ ] Status: 200 OK
- [ ] File size matches original (~15MB)
- [ ] File hash matches (if tracked)

### 4.3 Verify Expiration
```bash
# Wait 11 minutes (or modify S3Properties to 1 minute for testing)
sleep 660
curl -o expired-download.txt "$DOWNLOAD_URL"
```

**Expected**:
- [ ] Status: 403 Forbidden
- [ ] Error: "Request has expired"

---

## Test Suite 5: Data Persistence

### 5.1 Container Restart
```bash
# Restart all services
docker-compose restart

# Wait for health
sleep 30
```

**Expected**:
- [ ] All services restart successfully
- [ ] Health checks pass

### 5.2 Data Integrity After Restart
```bash
# Query metadata
curl -X GET http://localhost:8080/files/$FILE_ID \
  -H "Authorization: Bearer $JWT_TOKEN"
```

**Expected**:
- [ ] File metadata still exists
- [ ] Status still `COMPLETED`
- [ ] Can generate new presigned URL

### 5.3 Volume Persistence
```bash
# Stop and remove containers (but keep volumes)
docker-compose down

# Restart
docker-compose up -d

# Wait for health
sleep 30

# Verify data
docker exec -it cloud-drive-postgres psql -U clouduser -d metadatadb \
  -c "SELECT COUNT(*) FROM file_metadata;"
```

**Expected**:
- [ ] Data persists across full restart
- [ ] File count matches previous state

---

## Test Suite 6: Idempotency Validation

### 6.1 Duplicate Chunk Upload
```bash
# Upload same chunk twice
curl -X POST http://localhost:8080/files/upload/chunk \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -F "fileId=$FILE_ID" \
  -F "chunkNumber=1" \
  -F "chunk=@test-chunk-1.bin"

# Second upload (duplicate)
curl -X POST http://localhost:8080/files/upload/chunk \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -F "fileId=$FILE_ID" \
  -F "chunkNumber=1" \
  -F "chunk=@test-chunk-1.bin"
```

**Expected**:
- [ ] Both return 200 OK
- [ ] No duplicate rows in `chunk_metadata`
- [ ] Logs show "Chunk already exists. Skipping."

### 6.2 Duplicate Complete
```bash
# Complete already-completed upload
curl -X POST http://localhost:8080/files/upload/complete \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fileId": '$FILE_ID'}'
```

**Expected**:
- [ ] Status: 200 OK
- [ ] Response identical to first complete
- [ ] Logs show "File already completed. Returning success."
- [ ] No duplicate S3 operations

---

## Test Suite 7: Security Validation

### 7.1 Unauthorized Access (No Token)
```bash
curl -X GET http://localhost:8080/files/$FILE_ID/download
```

**Expected**:
- [ ] Status: 401 Unauthorized
- [ ] No presigned URL returned

### 7.2 Access File as Wrong User
```bash
# Register second user
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username": "attacker", "email": "bad@example.com", "password": "Pass123!"}'

# Login as attacker
ATTACKER_TOKEN=$(curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "attacker", "password": "Pass123!"}' | jq -r '.token')

# Try to access testuser's file
curl -X GET http://localhost:8080/files/$FILE_ID/download \
  -H "Authorization: Bearer $ATTACKER_TOKEN" \
  -H "X-User-Id: attacker"
```

**Expected**:
- [ ] Status: 403 Forbidden
- [ ] Error: "You are not authorized to access this file."
- [ ] Logs show access attempt with user mismatch

### 7.3 File ID Enumeration
```bash
# Try guessing file IDs
for i in {1..10}; do
  curl -s -o /dev/null -w "%{http_code}\n" \
    http://localhost:8080/files/$i/download \
    -H "Authorization: Bearer $ATTACKER_TOKEN" \
    -H "X-User-Id: attacker"
done
```

**Expected**:
- [ ] All return 403 or 404
- [ ] No information leakage about which IDs exist

---

## Test Suite 8: Observability Validation

### 8.1 Log Inspection
```bash
# Check file-service logs
docker logs file-service --tail 100 | grep -i "presigned"
docker logs file-service --tail 100 | grep -i "uploadId"
docker logs file-service --tail 100 | grep -i "aws"
```

**Expected**:
- [ ] NO presigned URLs in logs
- [ ] UploadIds only in INFO/DEBUG (not in prod)
- [ ] NO AWS credentials in logs

### 8.2 Structured Logging
```bash
# Verify log format
docker logs metadata-service --tail 50
```

**Expected**:
- [ ] Consistent timestamp format
- [ ] Log level present (INFO, WARN, ERROR)
- [ ] Contextual info (fileId, userId) present
- [ ] No stack traces for expected errors (403, 404)

---

## Summary Checklist

### Critical Path (Must Pass)
- [ ] All services start and become healthy
- [ ] JWT authentication works
- [ ] Multipart upload flow (initiate → chunk → complete) succeeds
- [ ] File persists in S3
- [ ] Metadata persists in PostgreSQL
- [ ] Presigned URL download works
- [ ] Data survives container restart

### Security (Must Pass)
- [ ] Unauthorized access blocked (401)
- [ ] Cross-user access blocked (403)
- [ ] No sensitive data in logs

### Resilience (Should Pass)
- [ ] Idempotent chunk upload
- [ ] Idempotent complete
- [ ] Services recover after restart

### Performance (Sanity Check)
- [ ] Health checks respond < 500ms
- [ ] Upload chunk < 5s (for 5MB chunk)
- [ ] Complete upload < 10s

---

## Known Limitations (Monitor These)

1. **No automatic cleanup of incomplete uploads** (S3 lifecycle rule required)
2. **No rate limiting** (can be DoS'd)
3. **No distributed tracing** (hard to debug cross-service issues)
4. **No circuit breakers** (cascading failures possible)
5. **No request deduplication** (duplicate requests hit DB)

---

## Next Steps After Validation

If all tests pass:
- [ ] Document test results
- [ ] Create automated integration tests (Testcontainers)
- [ ] Set up CI/CD pipeline
- [ ] Deploy to staging environment

If tests fail:
- [ ] Document failure scenario
- [ ] Add to known issues
- [ ] Create bug ticket
- [ ] Fix and re-test

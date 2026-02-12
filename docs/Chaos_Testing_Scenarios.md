# Chaos Engineering Test Scenarios

## Philosophy
We simulate real-world failures to validate system resilience. Each test follows the pattern:
1. **Inject Failure** (kill container, disconnect network, corrupt data)
2. **Observe Behavior** (logs, health checks, client errors)
3. **Recover** (restart, reconnect)
4. **Validate** (data integrity, state consistency)

---

## Scenario A: Kill FileService During Chunk Upload

### Objective
Verify that partial chunk uploads can be resumed without data corruption.

### Setup
```bash
# Start upload
FILE_ID=$(curl -X POST http://localhost:8080/files/upload/initiate \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fileName": "crash-test.bin", "fileSize": 20971520, "totalChunks": 4, "contentType": "application/octet-stream"}' \
  | jq -r '.fileId')

# Upload chunk 1
curl -X POST http://localhost:8080/files/upload/chunk \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -F "fileId=$FILE_ID" \
  -F "chunkNumber=1" \
  -F "chunk=@test-chunk-1.bin"

# Upload chunk 2
curl -X POST http://localhost:8080/files/upload/chunk \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -F "fileId=$FILE_ID" \
  -F "chunkNumber=2" \
  -F "chunk=@test-chunk-2.bin"
```

### Inject Failure
```bash
# Kill file-service mid-upload
docker kill file-service
```

### Expected Behavior
- [ ] Client receives connection error (502 Bad Gateway or timeout)
- [ ] Metadata Service remains healthy
- [ ] PostgreSQL remains healthy
- [ ] Chunks 1 & 2 persisted in database

### Recovery
```bash
# Restart file-service
docker-compose up -d file-service

# Wait for health
sleep 10
curl http://localhost:8082/actuator/health
```

### Validation
```bash
# Resume upload (chunk 3)
curl -X POST http://localhost:8080/files/upload/chunk \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -F "fileId=$FILE_ID" \
  -F "chunkNumber=3" \
  -F "chunk=@test-chunk-3.bin"

# Upload chunk 4
curl -X POST http://localhost:8080/files/upload/chunk \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -F "fileId=$FILE_ID" \
  -F "chunkNumber=4" \
  -F "chunk=@test-chunk-4.bin"

# Complete upload
curl -X POST http://localhost:8080/files/upload/complete \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fileId": '$FILE_ID'}'
```

**Expected**:
- [ ] Upload completes successfully
- [ ] All 4 chunks in database
- [ ] File status: `COMPLETED`
- [ ] File exists in S3

**Data Integrity Check**:
```bash
# Verify no duplicate chunks
docker exec -it cloud-drive-postgres psql -U clouduser -d metadatadb \
  -c "SELECT chunk_number, COUNT(*) FROM chunk_metadata WHERE file_id=$FILE_ID GROUP BY chunk_number HAVING COUNT(*) > 1;"
```
- [ ] No duplicates (0 rows returned)

---

## Scenario B: Kill MetadataService During Upload

### Objective
Verify that metadata service failure doesn't corrupt database state.

### Setup
```bash
# Initiate upload
FILE_ID=$(curl -X POST http://localhost:8080/files/upload/initiate \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fileName": "metadata-crash.bin", "fileSize": 10485760, "totalChunks": 2, "contentType": "application/octet-stream"}' \
  | jq -r '.fileId')

# Upload chunk 1
curl -X POST http://localhost:8080/files/upload/chunk \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -F "fileId=$FILE_ID" \
  -F "chunkNumber=1" \
  -F "chunk=@test-chunk-1.bin"
```

### Inject Failure
```bash
# Kill metadata-service
docker kill metadata-service
```

### Expected Behavior
```bash
# Try to upload chunk 2 (will fail)
curl -X POST http://localhost:8080/files/upload/chunk \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -F "fileId=$FILE_ID" \
  -F "chunkNumber=2" \
  -F "chunk=@test-chunk-2.bin"
```

**Expected**:
- [ ] Status: 500 Internal Server Error or 502 Bad Gateway
- [ ] Error message: "Metadata Service unavailable" or similar
- [ ] File Service logs show Feign exception
- [ ] S3 chunk upload may succeed (orphaned in S3)

### Recovery
```bash
# Restart metadata-service
docker-compose up -d metadata-service

# Wait for health
sleep 15
curl http://localhost:8083/actuator/health
```

### Validation
```bash
# Check database state
docker exec -it cloud-drive-postgres psql -U clouduser -d metadatadb \
  -c "SELECT id, file_name, status FROM file_metadata WHERE id=$FILE_ID;"
```

**Expected**:
- [ ] File exists
- [ ] Status: `ACTIVE` (not corrupted to invalid state)
- [ ] Only chunk 1 in `chunk_metadata`

```bash
# Resume upload
curl -X POST http://localhost:8080/files/upload/chunk \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -F "fileId=$FILE_ID" \
  -F "chunkNumber=2" \
  -F "chunk=@test-chunk-2.bin"

# Complete
curl -X POST http://localhost:8080/files/upload/complete \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fileId": '$FILE_ID'}'
```

**Expected**:
- [ ] Upload completes successfully
- [ ] No state corruption

---

## Scenario C: Kill PostgreSQL During Upload

### Objective
Verify graceful degradation when database is unavailable.

### Setup
```bash
# Initiate upload (will succeed, metadata in DB)
FILE_ID=$(curl -X POST http://localhost:8080/files/upload/initiate \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fileName": "db-crash.bin", "fileSize": 10485760, "totalChunks": 2, "contentType": "application/octet-stream"}' \
  | jq -r '.fileId')
```

### Inject Failure
```bash
# Kill PostgreSQL
docker kill cloud-drive-postgres
```

### Expected Behavior
```bash
# Try to upload chunk (will fail)
curl -X POST http://localhost:8080/files/upload/chunk \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -F "fileId=$FILE_ID" \
  -F "chunkNumber=1" \
  -F "chunk=@test-chunk-1.bin"
```

**Expected**:
- [ ] Status: 500 Internal Server Error
- [ ] Metadata Service health check fails
- [ ] File Service receives 500 from Metadata Service
- [ ] Logs show database connection errors

```bash
# Check service health
curl http://localhost:8083/actuator/health
```

**Expected**:
- [ ] Status: 503 Service Unavailable
- [ ] Health details show DB down

### Recovery
```bash
# Restart PostgreSQL
docker-compose up -d postgres

# Wait for DB to be ready
sleep 20

# Check metadata service health
curl http://localhost:8083/actuator/health
```

**Expected**:
- [ ] Metadata Service reconnects automatically (HikariCP retry)
- [ ] Health returns to UP

### Validation
```bash
# Retry chunk upload
curl -X POST http://localhost:8080/files/upload/chunk \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -F "fileId=$FILE_ID" \
  -F "chunkNumber=1" \
  -F "chunk=@test-chunk-1.bin"
```

**Expected**:
- [ ] Upload succeeds
- [ ] No data loss
- [ ] File metadata intact

---

## Scenario D: Kill Container During Complete-Upload

### Objective
Verify idempotency of complete operation.

### Setup
```bash
# Upload all chunks
FILE_ID=$(curl -X POST http://localhost:8080/files/upload/initiate \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fileName": "complete-crash.bin", "fileSize": 10485760, "totalChunks": 2, "contentType": "application/octet-stream"}' \
  | jq -r '.fileId')

curl -X POST http://localhost:8080/files/upload/chunk \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -F "fileId=$FILE_ID" \
  -F "chunkNumber=1" \
  -F "chunk=@test-chunk-1.bin"

curl -X POST http://localhost:8080/files/upload/chunk \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -F "fileId=$FILE_ID" \
  -F "chunkNumber=2" \
  -F "chunk=@test-chunk-2.bin"
```

### Inject Failure
```bash
# Start complete in background
curl -X POST http://localhost:8080/files/upload/complete \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fileId": '$FILE_ID'}' &

# Immediately kill file-service (race condition)
sleep 2
docker kill file-service
```

### Expected Behavior
**Scenario 1: S3 Complete Succeeded, Metadata Update Failed**
- [ ] S3 object exists
- [ ] Database status: `ACTIVE` (not `COMPLETED`)
- [ ] Orphaned file in S3

**Scenario 2: S3 Complete Failed**
- [ ] No S3 object
- [ ] Database status: `ACTIVE`
- [ ] Consistent state

### Recovery
```bash
# Restart file-service
docker-compose up -d file-service
sleep 10
```

### Validation
```bash
# Retry complete (idempotency test)
curl -X POST http://localhost:8080/files/upload/complete \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fileId": '$FILE_ID'}'
```

**Expected**:
- [ ] Status: 200 OK (or 500 if S3 orphaned)
- [ ] If S3 succeeded: Metadata updated to `COMPLETED`
- [ ] If S3 failed: Retry succeeds, file completed

**Data Integrity**:
```bash
# Check for orphaned S3 objects
aws s3 ls s3://$S3_BUCKET/uploads/ --recursive

# Check database
docker exec -it cloud-drive-postgres psql -U clouduser -d metadatadb \
  -c "SELECT id, status FROM file_metadata WHERE id=$FILE_ID;"
```

**Reconciliation**:
- [ ] If orphaned: Manual cleanup or reconciliation job needed
- [ ] Document as known issue: "Orphaned S3 objects possible on crash during complete"

---

## Scenario E: Network Partition Simulation

### Objective
Simulate slow network or packet loss.

### Setup (Using `tc` - Traffic Control)
```bash
# Slow down network to file-service
docker exec file-service tc qdisc add dev eth0 root netem delay 2000ms

# OR: Add packet loss
docker exec file-service tc qdisc add dev eth0 root netem loss 30%
```

### Expected Behavior
```bash
# Upload chunk (will be slow)
time curl -X POST http://localhost:8080/files/upload/chunk \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -F "fileId=$FILE_ID" \
  -F "chunkNumber=1" \
  -F "chunk=@test-chunk-1.bin"
```

**Expected**:
- [ ] Upload takes significantly longer (2s+ delay per packet)
- [ ] Eventually succeeds (no timeout if < 30s)
- [ ] OR: Times out if delay too high

### Cleanup
```bash
# Remove network delay
docker exec file-service tc qdisc del dev eth0 root
```

---

## Scenario F: Concurrent Upload Race Condition

### Objective
Test for race conditions in chunk upload.

### Setup
```bash
# Initiate upload
FILE_ID=$(curl -X POST http://localhost:8080/files/upload/initiate \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fileName": "race-test.bin", "fileSize": 10485760, "totalChunks": 2, "contentType": "application/octet-stream"}' \
  | jq -r '.fileId')
```

### Inject Concurrency
```bash
# Upload same chunk concurrently (10 times)
for i in {1..10}; do
  curl -X POST http://localhost:8080/files/upload/chunk \
    -H "Authorization: Bearer $JWT_TOKEN" \
    -F "fileId=$FILE_ID" \
    -F "chunkNumber=1" \
    -F "chunk=@test-chunk-1.bin" &
done

wait
```

### Expected Behavior
- [ ] All requests return 200 OK
- [ ] Only 1 row in `chunk_metadata` for chunk 1
- [ ] No database deadlocks
- [ ] Logs show "Chunk already exists" for duplicates

### Validation
```bash
# Check for duplicates
docker exec -it cloud-drive-postgres psql -U clouduser -d metadatadb \
  -c "SELECT chunk_number, COUNT(*) FROM chunk_metadata WHERE file_id=$FILE_ID GROUP BY chunk_number;"
```

**Expected**:
- [ ] chunk_number=1, count=1

---

## Summary: Expected System Behavior

| Failure Scenario | Expected Behavior | Recovery |
|------------------|-------------------|----------|
| FileService crash during chunk | Client error, metadata intact | Resume upload after restart |
| MetadataService crash | 500 error, S3 may have orphan | Resume after restart |
| PostgreSQL crash | 500 error, services degrade | Auto-reconnect on DB restart |
| Crash during complete | Possible S3 orphan | Retry complete (idempotent) |
| Network delay | Slow response, eventual success | N/A |
| Concurrent uploads | Idempotent, no duplicates | N/A |

---

## Known Weak Spots to Monitor

1. **Orphaned S3 Objects**: If crash occurs after S3 complete but before metadata update
   - **Mitigation**: S3 lifecycle rule to delete incomplete uploads after 7 days
   - **Future**: Reconciliation job to sync S3 and DB

2. **No Circuit Breaker**: Cascading failures possible if Metadata Service is slow
   - **Mitigation**: Add Resilience4j circuit breaker

3. **No Request Deduplication**: Duplicate requests hit database
   - **Mitigation**: Add Redis-based deduplication cache

4. **No Distributed Tracing**: Hard to debug cross-service failures
   - **Mitigation**: Add Spring Cloud Sleuth + Zipkin

5. **No Rate Limiting**: Can be DoS'd with many concurrent uploads
   - **Mitigation**: Add API Gateway rate limiting

---

## Future Monitoring Recommendations

### Prometheus Metrics to Add

**File Service**:
- `file_upload_duration_seconds` (histogram)
- `chunk_upload_total` (counter)
- `chunk_upload_failures_total` (counter)
- `s3_operation_duration_seconds` (histogram)
- `presigned_url_generation_total` (counter)

**Metadata Service**:
- `metadata_query_duration_seconds` (histogram)
- `database_connection_pool_active` (gauge)
- `state_transition_total` (counter, labeled by from/to state)
- `state_transition_failures_total` (counter)

**System-Wide**:
- `http_requests_total` (counter, labeled by service, endpoint, status)
- `http_request_duration_seconds` (histogram)
- `jvm_memory_used_bytes` (gauge)
- `jvm_gc_pause_seconds` (histogram)

### Alerts to Configure

- **High Error Rate**: `rate(http_requests_total{status=~"5.."}[5m]) > 0.05`
- **Slow Uploads**: `histogram_quantile(0.95, chunk_upload_duration_seconds) > 30`
- **DB Connection Pool Exhausted**: `database_connection_pool_active >= database_connection_pool_max`
- **Service Down**: `up{job="file-service"} == 0`

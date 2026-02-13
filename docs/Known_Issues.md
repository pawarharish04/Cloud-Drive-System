# Known Issues & Limitations

## Critical Issues (Must Address Before Production)

### 1. Orphaned S3 Objects on Crash
**Severity**: High  
**Impact**: Storage cost leakage, data inconsistency

**Scenario**:
If `file-service` crashes after S3 `completeMultipartUpload` succeeds but before metadata is updated to `COMPLETED`, the S3 object exists but the database shows `ACTIVE`.

**Current Behavior**:
- S3 object exists
- Database status: `ACTIVE`
- Client receives 500 error
- Retry of complete may fail (S3 already completed)

**Mitigation (Implemented)**:
- S3 Lifecycle Rule: Delete incomplete multipart uploads after 7 days
- Idempotency: Retry of complete is safe

**Future Fix**:
- Reconciliation job: Scan `ACTIVE` sessions older than X hours, check S3, update metadata
- Two-phase commit pattern (complex, may not be worth it)

---

### 2. No Automatic Cleanup of Failed Uploads
**Severity**: Medium  
**Impact**: Database bloat, orphaned metadata

**Scenario**:
User initiates upload but never completes it (abandons, network failure, etc.).

**Current Behavior**:
- Database entry remains in `PENDING` or `ACTIVE` state forever
- S3 incomplete multipart upload remains (cleaned by lifecycle rule)

**Mitigation (Implemented)**:
- S3 Lifecycle Rule handles S3 cleanup

**Future Fix**:
- Background job: Mark sessions older than 24 hours as `FAILED`
- API endpoint: `/upload/{fileId}/abort` for explicit cleanup

---

## Performance Limitations

### 3. No Rate Limiting
**Severity**: Medium  
**Impact**: DoS vulnerability

**Current Behavior**:
- Unlimited concurrent uploads per user
- Can exhaust DB connection pool
- Can exhaust S3 API quota

**Future Fix**:
- API Gateway rate limiting (e.g., 10 uploads/minute per user)
- Redis-based distributed rate limiter

---

### 4. No Circuit Breaker
**Severity**: Medium  
**Impact**: Cascading failures

**Current Behavior**:
- If Metadata Service is slow, File Service waits indefinitely
- Thread pool exhaustion possible

**Future Fix**:
- Add Resilience4j circuit breaker to Feign clients
- Fail fast after N consecutive failures

---

### 5. No Request Deduplication
**Severity**: Low  
**Impact**: Unnecessary DB load

**Current Behavior**:
- Duplicate chunk upload requests both hit database
- Idempotency check happens in DB (not before)

**Future Fix**:
- Redis-based request deduplication cache
- Check cache before hitting DB

---

## Observability Gaps

### 6. No Distributed Tracing
**Severity**: Medium  
**Impact**: Hard to debug cross-service issues

**Current Behavior**:
- Logs are per-service
- No correlation ID across services
- Hard to trace a single upload through the system

**Future Fix**:
- Add Spring Cloud Sleuth
- Integrate with Zipkin or Jaeger
- Add correlation IDs to all logs

---

### 7. No Metrics/Monitoring
**Severity**: Medium  
**Impact**: No visibility into system health

**Current Behavior**:
- No Prometheus metrics
- No dashboards
- No alerts

**Future Fix**:
- Add Micrometer for Prometheus metrics
- Create Grafana dashboards
- Set up alerts (high error rate, slow uploads, etc.)

---

## Security Gaps

### 8. No File Size Validation at Upload Time
**Severity**: Low  
**Impact**: User can upload larger file than declared

**Current Behavior**:
- User declares `fileSize` in initiate request
- No validation that actual uploaded data matches

**Future Fix**:
- Track cumulative chunk size
- Reject if exceeds declared size

---

### 9. No Virus Scanning
**Severity**: High (for production)  
**Impact**: Malware distribution

**Current Behavior**:
- Files uploaded directly to S3 without scanning

**Future Fix**:
- Integrate with ClamAV or AWS GuardDuty
- Scan files before marking as `COMPLETED`
- Quarantine suspicious files

---

### 10. JWT Secret in Environment Variable
**Severity**: Medium  
**Impact**: Secret exposure risk

**Current Behavior**:
- JWT secret in `.env` file
- Visible in `docker-compose` environment

**Future Fix**:
- Use AWS Secrets Manager or Vault
- Rotate secrets periodically

---

## Data Integrity Issues

### 11. No Checksum Validation
**Severity**: Medium  
**Impact**: Corrupted files undetected

**Current Behavior**:
- No MD5/SHA256 checksum validation
- Client could upload corrupted data

**Future Fix**:
- Client sends checksum with each chunk
- Server validates before storing
- Store final file checksum in metadata

---

### 12. No Chunk Order Validation
**Severity**: Low  
**Impact**: Chunks could be uploaded out of order

**Current Behavior**:
- Chunks can be uploaded in any order
- S3 handles ordering during complete

**Future Fix**:
- Enforce sequential chunk upload (optional)
- Or: Validate all chunks present before complete

---

## Scalability Limitations

### 13. Single PostgreSQL Instance
**Severity**: High (for production)  
**Impact**: Single point of failure

**Current Behavior**:
- One PostgreSQL container
- No replication
- No failover

**Future Fix**:
- Use AWS RDS with Multi-AZ
- Or: PostgreSQL replication (primary + replica)

---

### 14. No Horizontal Scaling
**Severity**: Medium  
**Impact**: Limited throughput

**Current Behavior**:
- Single instance of each service
- No load balancing

**Future Fix**:
- Deploy multiple instances behind load balancer
- Use Kubernetes for auto-scaling

---

## Operational Issues

### 15. No Backup Strategy
**Severity**: High (for production)  
**Impact**: Data loss risk

**Current Behavior**:
- No automated backups
- No disaster recovery plan

**Future Fix**:
- Automated PostgreSQL backups (daily)
- S3 versioning enabled
- Cross-region replication

---

### 16. No Health Check for S3
**Severity**: Low  
**Impact**: Service reports healthy even if S3 is down

**Current Behavior**:
- Health check only checks DB connection
- S3 failures only detected on upload

**Future Fix**:
- Add S3 health check (e.g., list bucket)
- Report degraded status if S3 unreachable

---

## Summary: Priority Matrix

| Issue | Severity | Effort | Priority |
|-------|----------|--------|----------|
| Orphaned S3 Objects | High | Medium | **P0** |
| No Virus Scanning | High | High | **P0** |
| Single DB Instance | High | Medium | **P0** |
| No Backup Strategy | High | Low | **P0** |
| No Distributed Tracing | Medium | Medium | P1 |
| No Circuit Breaker | Medium | Low | P1 |
| No Rate Limiting | Medium | Low | P1 |
| No Metrics | Medium | Medium | P1 |
| No Checksum Validation | Medium | Medium | P2 |
| JWT Secret Management | Medium | Medium | P2 |
| No Request Dedup | Low | Medium | P3 |
| No Chunk Order Validation | Low | Low | P3 |

---

## Monitoring Recommendations

### Alerts to Set Up

1. **High Error Rate**: `rate(http_requests_total{status=~"5.."}[5m]) > 0.05`
2. **Slow Uploads**: `histogram_quantile(0.95, chunk_upload_duration_seconds) > 30`
3. **DB Pool Exhausted**: `hikari_connections_active >= hikari_connections_max`
4. **Service Down**: `up{job="file-service"} == 0`
5. **Orphaned Uploads**: `count(file_metadata{status="ACTIVE", age_hours > 24}) > 10`

### Dashboards to Create

1. **Upload Metrics**: Success rate, duration, throughput
2. **System Health**: CPU, memory, disk, network
3. **Database**: Connection pool, query duration, deadlocks
4. **S3**: Upload rate, error rate, bandwidth

---

## Testing Gaps

### Not Covered by Current Tests

1. **Load Testing**: No tests for high concurrency (100+ uploads)
2. **Soak Testing**: No tests for long-running stability (24+ hours)
3. **Chaos Mesh**: No automated chaos testing (only manual)
4. **Contract Tests**: No API contract validation

### ✅ Recently Resolved (2026-02-13)

1. ~~Integration Tests~~ → **13 metadata-service tests + 5 file-service tests now pass**
   - Fixed Testcontainers Docker Desktop compatibility (upgraded to 1.21.4)
   - Fixed `LazyInitializationException` in `MetadataService.getUploadedChunks`
   - Fixed file-service test context loading (S3 auto-config exclusion + property alignment)

### Recommended Next Steps

1. Add JMeter/Gatling load tests
2. Set up CI/CD with automated testing
3. Add mutation testing (PIT)


# How The Project Works

## Failure Handling Strategy & Reliability Design

### 1. Design Philosophy
This system is designed with **consistency** and **robustness** as priority. We treat S3 as the physical source of truth and Metadata Service as the logical index.

### 2. Failure Scenarios Handling

#### A) S3 Upload Fails
Since S3 orchestrates the physical storage, any failure here is critical.
- **Action**: The `ChunkUploadService` catches `S3UploadFailedException`.
- **Metadata**: Remains in `ACTIVE` state.
- **Client**: Receives `502 Bad Gateway`. Can safely retry the upload operation.

#### B) S3 Complete Fails
- **Action**: Immediate failure response.
- **Metadata**: Remains in `ACTIVE` state.
- **Client**: Receives error. Can retry `complete` endpoint. Idempotency ensures safety.

#### C) Metadata Update Fails (After S3 Complete)
This is a "Partial Failure" scenario (Orphaned File).
- **Scenario**: S3 successfully assembled the file, but we failed to flip the DB flag to `COMPLETED`.
- **Action**: We log a **CRITICAL** error.
- **Client**: Receives `500 Internal Server Error`.
- **Resolution**: Future Reconciliation Job (to be built) will scan `ACTIVE` sessions older than X hours and check S3 existence to fix state.

### 3. Idempotency Protection
- **Chunks**: If a chunk is uploaded twice, the second call returns success immediately if it already exists in Metadata.
- **Completion**: If `/complete` is called on a `COMPLETED` file, it returns 200 OK immediately with the file details.

### 4. State Machine Validation
Strict lifecycle enforcement:
- `PENDING` -> `ACTIVE` (On first chunk)
- `ACTIVE` -> `COMPLETED` (On finish)
- `ACTIVE` -> `FAILED` / `ABORTED` (On error/cancel)
- Any other transition (e.g., adding chunk to COMPLETED file) is rejected with `409 Conflict`.

### 5. Exception Handling
We use a centralized `GlobalExceptionHandler` (`@RestControllerAdvice`) in all services.
- **404**: Resource/Session Not Found
- **409**: Conflict (State, Idempotency)
- **502**: Upstream (S3, Metadata) Error
- **500**: Internal Logic Error

Structured JSON response:
```json
{
  "timestamp": "2023-10-27T10:00:00",
  "errorCode": "INVALID_UPLOAD_STATE",
  "message": "Upload is already completed",
  "path": "/files/upload/chunk",
  "status": 409
}
```

# 🎯 Design Analysis: File Chunking in Cloud Drive System

## 📌 Overview
This document explains the architectural decision to implement file chunking in our distributed file storage system.

---

## 1️⃣ Why File Chunking is Necessary

### Problem with Monolithic Uploads

**Memory Constraints:**
- Uploading a 5GB file as a single blob requires the entire file to be loaded into memory on the server
- This can cause `OutOfMemoryError` and crash the service
- Example: With 4GB heap, only 1 concurrent large file upload is possible

**Network Reliability:**
- A single network interruption during a 2-hour upload means starting from scratch
- No way to resume from where it failed
- Poor user experience for large files

**Timeout Issues:**
- HTTP request timeouts (typically 30-60 seconds) make large file uploads impossible
- Load balancers and proxies often have strict timeout policies
- Gateway timeouts become common with files > 100MB

**Scalability Bottleneck:**
- Each upload ties up a server thread for the entire duration
- Limits concurrent users drastically
- Cannot horizontally scale effectively

### How Chunking Solves This

**Streaming Processing:**
- Process 5MB at a time instead of 5GB at once
- Constant memory footprint regardless of file size
- Predictable resource usage

**Resume Capability:**
- If chunk 47/100 fails, resume from chunk 47, not from the beginning
- Track upload progress in database
- Better user experience for unstable networks

**Parallel Uploads (Future):**
- Upload multiple chunks simultaneously
- Utilize full network bandwidth
- Reduce total upload time by 3-5x

**Better Resource Utilization:**
- Short-lived requests (seconds instead of hours)
- Free up threads quickly
- Support more concurrent users

---

## 2️⃣ How It Improves Scalability

| Aspect | Without Chunking | With Chunking |
|--------|------------------|---------------|
| **Memory per Upload** | Entire file size (e.g., 5GB) | Fixed chunk size (5MB) |
| **Thread Blocking Time** | Hours for large files | Seconds per chunk |
| **Concurrent Users** | Limited by memory (~10 users) | Limited by CPU/network (~1000+ users) |
| **Network Resilience** | Restart on failure | Resume from last chunk |
| **S3 Multipart Upload** | Not utilized | Fully leveraged |
| **Horizontal Scaling** | Difficult | Easy (stateless chunks) |

### Real-World Impact

**Scenario 1: Server with 4GB Heap**
- **Without Chunking**: Can handle 10 concurrent 400MB file uploads
- **With Chunking**: Can handle 800+ concurrent uploads (5MB chunks)

**Scenario 2: 1GB File Upload on 10Mbps Connection**
- **Without Chunking**: 13 minutes, restart on failure
- **With Chunking**: 13 minutes, resume from last chunk on failure

**Scenario 3: AWS S3 Optimization**
- S3 Multipart Upload is optimized for chunks (5MB-5GB)
- Enables parallel uploads and better throughput
- Automatic retry for failed chunks

---

## 3️⃣ Tradeoffs Introduced

### ✅ Benefits

1. **Scalability**: Handle 100x more concurrent uploads
2. **Resilience**: Resume failed uploads without starting over
3. **Memory Efficiency**: Constant memory usage regardless of file size
4. **User Experience**: Progress tracking, pause/resume capability
5. **S3 Optimization**: Leverage AWS multipart upload features

### ❌ Costs

1. **Complexity**: More complex state management (tracking chunks)
2. **Storage Overhead**: Need to track chunk metadata in database
3. **Network Overhead**: More HTTP requests (overhead for small files)
4. **Consistency Challenges**: Need to handle partial upload cleanup
5. **Development Time**: More code to write and test

### 🎯 Mitigation Strategy

- **Use chunking only for files > 5MB** (direct upload for smaller files)
- **Implement automatic cleanup** for abandoned uploads (24-hour TTL)
- **Use Redis for session tracking** (fast, distributed, auto-expiry)
- **Validate chunks independently** (checksum verification)

---

## 4️⃣ Architecture Design

### High-Level Flow

```
┌─────────┐      ┌──────────────┐      ┌──────────────┐      ┌─────────┐
│ Client  │─────▶│ API Gateway  │─────▶│ File Service │─────▶│   S3    │
└─────────┘      └──────────────┘      └──────────────┘      └─────────┘
                                              │
                                              ▼
                                       ┌──────────────────┐
                                       │ Metadata Service │
                                       └──────────────────┘
```

### Detailed Upload Flow

```
1. Client → File Service: POST /files/initiate-upload
   Request: { fileName, fileSize, contentType }
   Response: { uploadId, chunkSize }

2. Client → File Service: POST /files/upload-chunk
   Request: { uploadId, chunkNumber, chunkData }
   Response: { chunkNumber, etag, status }
   
   (Repeat for all chunks)

3. Client → File Service: POST /files/complete-upload
   Request: { uploadId }
   Response: { fileId, fileUrl, metadata }

4. File Service → Metadata Service: POST /metadata
   Request: { fileId, fileName, size, chunks[] }
   Response: { metadataId, status }
```

### Key Components

**File Service:**
- `ChunkUploadController` - REST endpoints
- `ChunkUploadService` - Business logic
- `S3MultipartService` - S3 multipart wrapper
- `UploadSession` entity - Track in-progress uploads

**Metadata Service:**
- Store chunk metadata (chunk number, ETag, size)
- Link chunks to final file metadata

**Exception Handling:**
- `ChunkUploadException` - Chunk-specific errors
- `UploadSessionNotFoundException` - Invalid uploadId
- Cleanup logic for abandoned uploads

---

## 5️⃣ Configuration Decisions

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| **Chunk Size** | 5MB | AWS S3 minimum for multipart upload |
| **Session Storage** | Redis (fallback: H2) | Fast, distributed, auto-expiry |
| **Session TTL** | 24 hours | Balance between user convenience and cleanup |
| **Max File Size** | 5GB | Reasonable limit for initial version |
| **Chunking Threshold** | 5MB | Files < 5MB use direct upload |

---

## 6️⃣ Future Enhancements

1. **Parallel Chunk Upload**: Upload multiple chunks simultaneously
2. **Client-Side Chunking**: Reduce server load by chunking on client
3. **Deduplication**: Check if chunks already exist (save storage)
4. **Compression**: Compress chunks before upload
5. **Encryption**: Encrypt chunks at rest and in transit

---

## 7️⃣ Interview Talking Points

**Question**: "Why did you implement file chunking?"

**Answer**: 
"We implemented file chunking to solve three critical problems:

1. **Scalability**: Without chunking, uploading a 5GB file would load the entire file into memory, limiting us to ~10 concurrent uploads on a 4GB server. With 5MB chunks, we can handle 800+ concurrent uploads.

2. **Resilience**: If a network failure occurs during a 2-hour upload, users had to restart from scratch. With chunking, they resume from the last successful chunk, improving user experience dramatically.

3. **AWS S3 Optimization**: S3's multipart upload API is designed for chunks, enabling parallel uploads and better throughput. We leverage this to reduce upload times by 3-5x in the future.

The tradeoff is increased complexity in state management, but we mitigate this with Redis for session tracking and automatic cleanup of abandoned uploads after 24 hours."

---

**Document Version**: v1.0  
**Last Updated**: 2026-02-11  
**Author**: Cloud Drive System Team

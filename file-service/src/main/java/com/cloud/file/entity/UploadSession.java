package com.cloud.file.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity to track in-progress multipart uploads.
 * Stores session information for resumable uploads.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UploadSession {

    private String uploadId; // S3 multipart upload ID
    private String fileName;
    private Long fileSize;
    private String contentType;
    private String owner;
    private String s3Key; // S3 object key
    private Integer chunkSize;
    private Integer totalChunks;

    @Builder.Default
    private List<ChunkInfo> uploadedChunks = new ArrayList<>();

    private LocalDateTime initiatedAt;
    private LocalDateTime lastUpdatedAt;
    private String status; // INITIATED, IN_PROGRESS, COMPLETED, FAILED

    /**
     * Inner class to track individual chunk information
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ChunkInfo {
        private Integer chunkNumber;
        private String etag; // S3 ETag for the chunk
        private Long size;
        private LocalDateTime uploadedAt;
    }

    /**
     * Add a chunk to the uploaded chunks list
     */
    public void addChunk(ChunkInfo chunkInfo) {
        if (this.uploadedChunks == null) {
            this.uploadedChunks = new ArrayList<>();
        }
        this.uploadedChunks.add(chunkInfo);
        this.lastUpdatedAt = LocalDateTime.now();
    }

    /**
     * Check if all chunks have been uploaded
     */
    public boolean isComplete() {
        return uploadedChunks != null && uploadedChunks.size() == totalChunks;
    }

    /**
     * Get the next expected chunk number
     */
    public int getNextChunkNumber() {
        return uploadedChunks != null ? uploadedChunks.size() + 1 : 1;
    }
}

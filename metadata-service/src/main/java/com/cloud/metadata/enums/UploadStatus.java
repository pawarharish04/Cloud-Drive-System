package com.cloud.metadata.enums;

public enum UploadStatus {
    PENDING, // Upload initiated, awaiting first chunk
    ACTIVE, // At least one chunk received
    COMPLETED, // All chunks uploaded and merged
    FAILED, // Upload failed due to error
    ABORTED // Upload cancelled by user or system
}

package com.cloud.file.exception;

/**
 * Exception thrown when upload session is not found
 */
public class UploadSessionNotFoundException extends RuntimeException {

    public UploadSessionNotFoundException(String message) {
        super(message);
    }

    public UploadSessionNotFoundException(String uploadId) {
        super("Upload session not found: " + uploadId);
    }
}

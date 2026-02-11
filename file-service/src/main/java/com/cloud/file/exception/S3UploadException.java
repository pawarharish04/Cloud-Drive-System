package com.cloud.file.exception;

/**
 * Custom exception for S3 multipart upload operations
 */
public class S3UploadException extends RuntimeException {

    public S3UploadException(String message) {
        super(message);
    }

    public S3UploadException(String message, Throwable cause) {
        super(message, cause);
    }
}

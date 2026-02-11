package com.cloud.file.exception;

public class S3UploadFailedException extends FileStorageException {
    public S3UploadFailedException(String message, Throwable cause) {
        super(message, "S3_UPLOAD_FAILED", cause);
    }
}

package com.cloud.file.exception;

public class InvalidUploadStateException extends FileStorageException {
    public InvalidUploadStateException(String message) {
        super(message, "INVALID_UPLOAD_STATE");
    }
}

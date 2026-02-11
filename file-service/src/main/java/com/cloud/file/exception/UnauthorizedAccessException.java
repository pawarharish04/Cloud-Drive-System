package com.cloud.file.exception;

public class UnauthorizedAccessException extends FileStorageException {
    public UnauthorizedAccessException(String message) {
        super(message, "ACCESS_DENIED");
    }
}

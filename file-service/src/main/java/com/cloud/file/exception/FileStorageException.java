package com.cloud.file.exception;

import lombok.Getter;

@Getter
public abstract class FileStorageException extends RuntimeException {
    private final String errorCode;

    public FileStorageException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public FileStorageException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}

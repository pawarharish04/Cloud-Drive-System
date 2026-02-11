package com.cloud.file.exception;

/**
 * Exception thrown when chunk upload fails
 */
public class ChunkUploadException extends RuntimeException {

    public ChunkUploadException(String message) {
        super(message);
    }

    public ChunkUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.cloud.file.exception;

public class MetadataClientException extends FileStorageException {
    public MetadataClientException(String message, Throwable cause) {
        super(message, "METADATA_CLIENT_ERROR", cause);
    }
}

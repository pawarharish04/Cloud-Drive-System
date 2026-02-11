package com.cloud.metadata.exception;

public class ResourceNotFoundException extends MetadataException {
    public ResourceNotFoundException(String message) {
        super(message, "RESOURCE_NOT_FOUND");
    }
}

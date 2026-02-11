package com.cloud.metadata.exception;

import lombok.Getter;

@Getter
public abstract class MetadataException extends RuntimeException {
    private final String errorCode;

    public MetadataException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
}

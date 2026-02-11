package com.cloud.metadata.exception;

public class IllegalStateTransitionException extends MetadataException {
    public IllegalStateTransitionException(String message) {
        super(message, "ILLEGAL_STATE_TRANSITION");
    }
}

package com.cloud.metadata.exception;

public class ChunkAlreadyExistsException extends MetadataException {
    public ChunkAlreadyExistsException(String message) {
        super(message, "CHUNK_ALREADY_EXISTS");
    }
}

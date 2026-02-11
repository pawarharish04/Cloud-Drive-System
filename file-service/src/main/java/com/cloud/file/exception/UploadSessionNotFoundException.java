package com.cloud.file.exception;

public class UploadSessionNotFoundException extends FileStorageException {
    public UploadSessionNotFoundException(String id) {
        super("Upload session not found for ID: " + id, "UPLOAD_SESSION_NOT_FOUND");
    }
}

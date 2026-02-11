package com.cloud.file.repository;

import com.cloud.file.entity.UploadSession;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory repository for tracking active upload sessions.
 * In production, this should be replaced with Redis or a persistent DB.
 */
@Repository
public class UploadSessionRepository {

    private final ConcurrentHashMap<String, UploadSession> sessions = new ConcurrentHashMap<>();

    public UploadSession save(UploadSession session) {
        sessions.put(session.getUploadId(), session);
        return session;
    }

    public Optional<UploadSession> findById(String uploadId) {
        return Optional.ofNullable(sessions.get(uploadId));
    }

    public void deleteById(String uploadId) {
        sessions.remove(uploadId);
    }
}

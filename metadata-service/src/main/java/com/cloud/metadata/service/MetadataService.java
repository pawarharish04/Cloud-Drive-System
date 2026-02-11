package com.cloud.metadata.service;

import com.cloud.metadata.dto.FileMetadataRequest;
import com.cloud.metadata.dto.FileMetadataResponse;
import com.cloud.metadata.entity.ChunkMetadata;
import com.cloud.metadata.entity.FileMetadata;
import com.cloud.metadata.enums.UploadStatus;
import com.cloud.metadata.exception.ChunkAlreadyExistsException;
import com.cloud.metadata.exception.IllegalStateTransitionException;
import com.cloud.metadata.exception.ResourceNotFoundException;
import com.cloud.metadata.repository.ChunkMetadataRepository;
import com.cloud.metadata.repository.FileMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetadataService {

        private final FileMetadataRepository fileRepository;
        private final ChunkMetadataRepository chunkRepository;

        /**
         * Initiate a new upload session
         */
        @Transactional
        public Long initiateSession(String fileName, String userId, String uploadId, Integer totalChunks, Long size,
                        String contentType) {
                FileMetadata metadata = FileMetadata.builder()
                                .fileName(fileName)
                                .owner(userId)
                                .uploadId(uploadId)
                                .totalChunks(totalChunks)
                                .size(size)
                                .fileType(contentType)
                                .status(UploadStatus.PENDING)
                                .build();

                return fileRepository.save(metadata).getId();
        }

        /**
         * Add a chunk to an active session
         */
        @Transactional
        public void addChunk(Long fileId, Integer chunkNumber, String etag, Long size) {
                FileMetadata file = fileRepository.findById(fileId)
                                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));

                // Idempotency Check: prevent duplicate chunks
                boolean chunkExists = file.getChunks().stream()
                                .anyMatch(c -> c.getChunkNumber().equals(chunkNumber));

                if (chunkExists) {
                        log.info("Chunk {} already exists for file {}. Skipping.", chunkNumber, fileId);
                        // Optionally verify ETag matches if strict?
                        return;
                }

                validateStateTransition(file.getStatus(), UploadStatus.ACTIVE);

                // If this is the first chunk, update status to ACTIVE
                if (file.getStatus() == UploadStatus.PENDING) {
                        file.setStatus(UploadStatus.ACTIVE);
                        fileRepository.save(file);
                }

                ChunkMetadata chunk = ChunkMetadata.builder()
                                .fileMetadata(file) // Link to parent
                                .chunkNumber(chunkNumber)
                                .etag(etag)
                                .size(size)
                                .build();

                file.getChunks().add(chunk);
                fileRepository.save(file);
        }

        /**
         * Get all uploaded chunks for a file
         */
        @Transactional(readOnly = true)
        public List<ChunkMetadata> getUploadedChunks(Long fileId) {
                FileMetadata file = fileRepository.findById(fileId)
                                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));
                return file.getChunks();
        }

        /**
         * Complete the upload session
         */
        @Transactional
        public void completeSession(Long fileId) {
                FileMetadata file = fileRepository.findById(fileId)
                                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));

                // Idempotency Check
                if (file.getStatus() == UploadStatus.COMPLETED) {
                        log.info("File {} is already completed. Returning success.", fileId);
                        return;
                }

                validateStateTransition(file.getStatus(), UploadStatus.COMPLETED);

                // Additional Validation: Check if all chunks are present
                if (file.getChunks().size() != file.getTotalChunks()) {
                        throw new IllegalStateException("Cannot complete session. Missing chunks. Expected: " +
                                        file.getTotalChunks() + ", Found: " + file.getChunks().size());
                }

                file.setStatus(UploadStatus.COMPLETED);
                fileRepository.save(file);
        }

        /**
         * Mark session as failed
         */
        @Transactional
        public void markFailed(Long fileId) {
                updateStatus(fileId, UploadStatus.FAILED);
        }

        /**
         * Abort session
         */
        @Transactional
        public void abortSession(Long fileId) {
                updateStatus(fileId, UploadStatus.ABORTED);
        }

        private void updateStatus(Long fileId, UploadStatus newStatus) {
                FileMetadata file = fileRepository.findById(fileId)
                                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));

                validateStateTransition(file.getStatus(), newStatus);

                file.setStatus(newStatus);
                fileRepository.save(file);
        }

        private void validateStateTransition(UploadStatus current, UploadStatus target) {
                // Allow idempotent actions (ACTIVE -> ACTIVE)
                if (current == target)
                        return;

                // PENDING -> ACTIVE
                if (current == UploadStatus.PENDING && target == UploadStatus.ACTIVE)
                        return;

                // ACTIVE -> COMPLETED, FAILED, ABORTED
                if (current == UploadStatus.ACTIVE && (target == UploadStatus.COMPLETED ||
                                target == UploadStatus.FAILED ||
                                target == UploadStatus.ABORTED))
                        return;

                // PENDING -> ABORTED (Cancellation before start)
                if (current == UploadStatus.PENDING && target == UploadStatus.ABORTED)
                        return;

                throw new IllegalStateTransitionException("Invalid state transition from " + current + " to " + target);
        }

        // Legacy/Generic save method (kept for compatibility or basic metadata saving)
        @Transactional
        public FileMetadataResponse saveMetadata(FileMetadataRequest request) {
                FileMetadata metadata = FileMetadata.builder()
                                .fileName(request.getFileName())
                                .fileType(request.getFileType())
                                .size(request.getSize())
                                .s3Key(request.getS3Key())
                                .owner(request.getOwner())
                                .status(UploadStatus.COMPLETED) // Assume completed if direct save
                                .build();

                FileMetadata saved = fileRepository.save(metadata);
                return mapToResponse(saved);
        }

        public FileMetadataResponse getFileById(Long fileId) {
                FileMetadata file = fileRepository.findById(fileId)
                                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));
                return mapToResponse(file);
        }

        public List<FileMetadataResponse> getFilesByOwner(String owner) {
                return fileRepository.findByOwner(owner).stream()
                                .map(this::mapToResponse)
                                .collect(Collectors.toList());
        }

        private FileMetadataResponse mapToResponse(FileMetadata file) {
                return FileMetadataResponse.builder()
                                .id(file.getId())
                                .fileName(file.getFileName())
                                .fileType(file.getFileType())
                                .size(file.getSize())
                                .s3Key(file.getS3Key())
                                .owner(file.getOwner())
                                .uploadedAt(file.getUpdatedAt())
                                .status(file.getStatus() != null ? file.getStatus().name() : null)
                                .uploadId(file.getUploadId())
                                .build();
        }
}

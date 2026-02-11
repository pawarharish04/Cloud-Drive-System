package com.cloud.file.service;

import com.cloud.file.client.MetadataClient;
import com.cloud.file.client.dto.FileMetadataResponse;
import com.cloud.file.client.dto.MetadataAddChunkRequest;
import com.cloud.file.client.dto.MetadataChunkResponse;
import com.cloud.file.client.dto.MetadataInitiateRequest;
import com.cloud.file.dto.*;
import com.cloud.file.exception.*;
import com.cloud.file.storage.S3MultipartService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkUploadService {

        private final S3MultipartService s3MultipartService;
        private final MetadataClient metadataClient;

        @Value("${app.chunk.size:5242880}") // Default 5MB
        private long defaultChunkSize;

        /**
         * Initiate Upload
         */
        public InitiateUploadResponse initiateUpload(InitiateUploadRequest request) {
                log.info("Initiating upload for file: {}", request.getFileName());

                try {
                        // 1. Initiate Multipart Upload in S3
                        S3MultipartService.MultipartInitResult initResult = s3MultipartService
                                        .initiateMultipartUpload(request.getFileName(), request.getContentType());
                        String s3UploadId = initResult.getUploadId();
                        String s3Key = initResult.getS3Key();

                        // 2. Calculate chunks
                        int totalChunks = (int) Math.ceil((double) request.getFileSize() / defaultChunkSize);

                        // 3. Create Session in Metadata Service
                        MetadataInitiateRequest metadataRequest = MetadataInitiateRequest.builder()
                                        .fileName(request.getFileName())
                                        .userId(request.getOwner())
                                        .uploadId(s3UploadId)
                                        .s3Key(s3Key)
                                        .totalChunks(totalChunks)
                                        .size(request.getFileSize())
                                        .contentType(request.getContentType())
                                        .build();

                        Long fileId = metadataClient.initiateSession(metadataRequest);

                        log.info("Upload initiated. FileId: {}, S3UploadId: {}", fileId, s3UploadId);

                        return InitiateUploadResponse.builder()
                                        .fileId(String.valueOf(fileId))
                                        .uploadId(s3UploadId)
                                        .fileName(request.getFileName())
                                        .fileSize(request.getFileSize())
                                        .chunkSize((int) defaultChunkSize)
                                        .totalChunks(totalChunks)
                                        .message("Upload session initiated")
                                        .build();

                } catch (S3UploadFailedException e) {
                        throw e; // Bubble up S3 errors
                } catch (FeignException e) {
                        throw new MetadataClientException("Failed to initiate metadata session", e);
                } catch (Exception e) {
                        throw new FileStorageException("Unexpected error during initiation", "INTERNAL_ERROR", e) {
                        };
                }
        }

        /**
         * Upload Chunk
         */
        public ChunkUploadResponse uploadChunk(ChunkUploadRequest request) {
                Long fileId = parseFileId(request.getFileId());

                // 1. Get Session Details
                FileMetadataResponse fileMetadata = getMetadataSafely(fileId);

                // Validation
                if ("COMPLETED".equals(fileMetadata.getStatus())) {
                        throw new InvalidUploadStateException("Upload is already completed. Cannot add more chunks.");
                }
                if ("FAILED".equals(fileMetadata.getStatus()) || "ABORTED".equals(fileMetadata.getStatus())) {
                        throw new InvalidUploadStateException(
                                        "Upload is in " + fileMetadata.getStatus() + " state. Cannot add chunks.");
                }

                try {
                        // 2. Upload to S3
                        String etag = s3MultipartService.uploadPart(fileMetadata.getUploadId(), fileMetadata.getS3Key(),
                                        request.getChunkNumber(), request.getChunkData());

                        // 3. Update Metadata Service
                        MetadataAddChunkRequest chunkRequest = MetadataAddChunkRequest.builder()
                                        .chunkNumber(request.getChunkNumber())
                                        .etag(etag)
                                        .size((long) request.getChunkData().length)
                                        .build();

                        metadataClient.addChunk(fileId, chunkRequest);

                        return ChunkUploadResponse.builder()
                                        .uploadId(fileMetadata.getUploadId())
                                        .chunkNumber(request.getChunkNumber())
                                        .etag(etag)
                                        .chunkSize((long) request.getChunkData().length)
                                        .status("UPLOADED")
                                        .message("Chunk uploaded successfully")
                                        .build();

                } catch (S3UploadFailedException e) {
                        log.error("S3 Upload Failed for fileId: {}, chunk: {}", fileId, request.getChunkNumber());
                        throw e;
                } catch (FeignException e) {
                        if (e.status() == 409) {
                                // Idempotency: Duplicate chunk? Or Invalid State?
                                // Since we checked state above, likely duplicate chunk (which MetadataService
                                // handles with idempotency now, but if it returns 409 for something else...)
                                // MetadataService returns 409 for IllegalStateTransition.
                                // We should log and rethrow.
                                throw new InvalidUploadStateException(
                                                "Metadata rejected chunk upload: " + e.getMessage());
                        }
                        throw new MetadataClientException(
                                        "Failed to update metadata for chunk " + request.getChunkNumber(), e);
                } catch (Exception e) {
                        log.error("Unexpected error uploading chunk for fileId: {}", fileId, e);
                        throw new FileStorageException("Failed to upload chunk", "CHUNK_UPLOAD_FAILED", e) {
                        };
                }
        }

        /**
         * Complete Upload
         */
        public CompleteUploadResponse completeUpload(CompleteUploadRequest request) {
                Long fileId = parseFileId(request.getFileId());

                // 1. Get Session Details
                FileMetadataResponse fileMetadata = getMetadataSafely(fileId);

                // Idempotency Check
                if ("COMPLETED".equals(fileMetadata.getStatus())) {
                        log.info("File {} already completed. Returning success (Idempotent).", fileId);
                        return buildCompleteResponse(fileMetadata, fileMetadata.getTotalChunks()); // We might catch
                                                                                                   // chunks count if
                                                                                                   // included in
                                                                                                   // response
                }

                if (!"ACTIVE".equals(fileMetadata.getStatus()) && !"PENDING".equals(fileMetadata.getStatus())) {
                        throw new InvalidUploadStateException(
                                        "Cannot complete upload in state: " + fileMetadata.getStatus());
                }

                try {
                        // 2. Get chunks from Metadata
                        List<MetadataChunkResponse> chunks = metadataClient.getUploadedChunks(fileId);

                        // Validate completeness (Metadata Service also does this, but failing fast here
                        // saves an S3 call)
                        // Note: Metadata Service getUploadedChunks returns list. We assume simple count
                        // check.
                        // S3 requires all parts.

                        List<S3MultipartService.CompletedPartInfo> s3Parts = chunks.stream()
                                        .sorted(Comparator.comparingInt(MetadataChunkResponse::getChunkNumber))
                                        .map(c -> new S3MultipartService.CompletedPartInfo(c.getChunkNumber(),
                                                        c.getEtag()))
                                        .collect(Collectors.toList());

                        // 3. Complete in S3
                        log.info("Completing S3 upload for fileId: {}", fileId);
                        String fileUrl = s3MultipartService.completeMultipartUpload(fileMetadata.getUploadId(),
                                        fileMetadata.getS3Key(), s3Parts);

                        // 4. Finalize Metadata
                        metadataClient.completeSession(fileId);

                        return CompleteUploadResponse.builder()
                                        .fileId(String.valueOf(fileId))
                                        .fileName(fileMetadata.getFileName())
                                        .fileUrl(fileUrl)
                                        .fileSize(fileMetadata.getSize())
                                        .totalChunks(s3Parts.size())
                                        .status("COMPLETED")
                                        .message("File uploaded and assembled successfully")
                                        .build();

                } catch (S3UploadFailedException e) {
                        log.error("S3 Completion Failed for fileId: {}", fileId, e);
                        // We do NOT abort automatically here to allow retries.
                        throw e;
                } catch (FeignException e) {
                        if (e.status() == 409) {
                                // Metadata rejected completion (e.g. missing chunks)
                                throw new InvalidUploadStateException(
                                                "Metadata rejected completion: " + e.getMessage());
                        }
                        log.error("Metadata Completion Failed for fileId: {}. S3 IS COMPLETE but Metadata is not!",
                                        fileId, e);
                        // CRITICAL: S3 is complete, but Metadata failed.
                        // We throw 500. System inconsistency.
                        throw new MetadataClientException("Failed to finalize metadata. File is uploaded to S3.", e);
                } catch (Exception e) {
                        log.error("Unexpected error completing upload for fileId: {}", fileId, e);
                        throw new FileStorageException("Failed to complete upload", "COMPLETION_FAILED", e) {
                        };
                }
        }

        private Long parseFileId(String fileIdStr) {
                try {
                        return Long.parseLong(fileIdStr);
                } catch (NumberFormatException e) {
                        throw new UploadSessionNotFoundException("Invalid ID format: " + fileIdStr);
                }
        }

        private FileMetadataResponse getMetadataSafely(Long fileId) {
                try {
                        FileMetadataResponse response = metadataClient.getFile(fileId);
                        if (response == null)
                                throw new UploadSessionNotFoundException(String.valueOf(fileId));
                        return response;
                } catch (FeignException.NotFound e) {
                        throw new UploadSessionNotFoundException(String.valueOf(fileId));
                } catch (FeignException e) {
                        throw new MetadataClientException("Failed to retrieve session", e);
                }
        }

        private CompleteUploadResponse buildCompleteResponse(FileMetadataResponse meta, int totalChunks) {
                return CompleteUploadResponse.builder()
                                .fileId(String.valueOf(meta.getId()))
                                .fileName(meta.getFileName())
                                .fileUrl("https://s3..." + meta.getS3Key()) // Construct URL if not available or fetch
                                .fileSize(meta.getSize())
                                .totalChunks(totalChunks)
                                .status("COMPLETED")
                                .message("File already uploaded")
                                .build();
        }
}

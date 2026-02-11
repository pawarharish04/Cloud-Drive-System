package com.cloud.file.service;

import com.cloud.file.client.MetadataClient;
import com.cloud.file.client.dto.FileMetadataResponse;
import com.cloud.file.client.dto.MetadataAddChunkRequest;
import com.cloud.file.client.dto.MetadataInitiateRequest;
import com.cloud.file.dto.*;
import com.cloud.file.exception.ChunkUploadException;
import com.cloud.file.storage.S3MultipartService;
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
         * Step 1: Initiate Upload
         */
        public InitiateUploadResponse initiateUpload(InitiateUploadRequest request) {
                log.info("Initiating upload for file: {}", request.getFileName());

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
                                .uploadId(s3UploadId) // Store S3 ID for recovery
                                .s3Key(s3Key) // Store S3 Key for recovery
                                .totalChunks(totalChunks)
                                .size(request.getFileSize())
                                .contentType(request.getContentType())
                                .build();

                Long fileId = metadataClient.initiateSession(metadataRequest);

                return InitiateUploadResponse.builder()
                                .fileId(String.valueOf(fileId))
                                .uploadId(s3UploadId)
                                .fileName(request.getFileName())
                                .fileSize(request.getFileSize())
                                .chunkSize((int) defaultChunkSize)
                                .totalChunks(totalChunks)
                                .message("Upload session initiated")
                                .build();
        }

        /**
         * Step 2: Upload Chunk
         */
        public ChunkUploadResponse uploadChunk(ChunkUploadRequest request) {
                Long fileId = Long.parseLong(request.getFileId());

                // 1. Get Session Details from Metadata Service
                FileMetadataResponse fileMetadata = metadataClient.getFile(fileId);
                if (fileMetadata == null) {
                        throw new ChunkUploadException("File session not found for ID: " + fileId);
                }

                // S3 requires uploadId and key.
                // We trusted metadata service to persist these.
                String s3UploadId = fileMetadata.getUploadId();
                String s3Key = fileMetadata.getS3Key();

                try {
                        // 2. Upload to S3
                        String etag = s3MultipartService.uploadPart(s3UploadId, s3Key, request.getChunkNumber(),
                                        request.getChunkData());

                        // 3. Update Metadata Service
                        MetadataAddChunkRequest chunkRequest = MetadataAddChunkRequest.builder()
                                        .chunkNumber(request.getChunkNumber())
                                        .etag(etag)
                                        .size((long) request.getChunkData().length)
                                        .build();

                        metadataClient.addChunk(fileId, chunkRequest);

                        return ChunkUploadResponse.builder()
                                        .uploadId(s3UploadId)
                                        .chunkNumber(request.getChunkNumber())
                                        .etag(etag)
                                        .chunkSize((long) request.getChunkData().length)
                                        .status("UPLOADED")
                                        .message("Chunk uploaded successfully")
                                        .build();

                } catch (Exception e) {
                        log.error("Chunk upload failed for fileId: {}, chunk: {}", fileId, request.getChunkNumber(), e);
                        throw new ChunkUploadException("Failed to upload chunk " + request.getChunkNumber(), e);
                }
        }

        /**
         * Step 3: Complete Upload
         */
        public CompleteUploadResponse completeUpload(CompleteUploadRequest request) {
                Long fileId = Long.parseLong(request.getFileId());

                // 1. Get Session Details
                FileMetadataResponse fileMetadata = metadataClient.getFile(fileId);
                if (fileMetadata == null) {
                        throw new ChunkUploadException("File session not found for ID: " + fileId);
                }

                try {
                        // 2. Get all uploaded chunks from Metadata Service
                        var chunks = metadataClient.getUploadedChunks(fileId);

                        // Map to S3 service's expected type
                        List<S3MultipartService.CompletedPartInfo> s3Parts = chunks.stream()
                                        .sorted(Comparator.comparingInt(
                                                        com.cloud.file.client.dto.MetadataChunkResponse::getChunkNumber))
                                        .map(c -> new S3MultipartService.CompletedPartInfo(c.getChunkNumber(),
                                                        c.getEtag()))
                                        .collect(Collectors.toList());

                        // 3. Complete in S3
                        // Note: S3 complete verifies part count/etags internally.
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

                } catch (Exception e) {
                        log.error("Failed to complete upload for fileId: {}", fileId, e);
                        // Optional: call metadataClient.abortSession(fileId) if we want to explicitly
                        // fail it
                        throw new ChunkUploadException("Failed to complete upload", e);
                }
        }
}

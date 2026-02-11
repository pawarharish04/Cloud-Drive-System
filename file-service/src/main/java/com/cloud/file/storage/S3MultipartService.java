package com.cloud.file.storage;

import com.cloud.file.exception.S3UploadException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service to handle S3 multipart upload operations.
 * Wraps AWS S3 SDK for better abstraction and error handling.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class S3MultipartService {

    private final S3Client s3Client;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    /**
     * Data class for initiation response
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class MultipartInitResult {
        private String uploadId;
        private String s3Key;
    }

    /**
     * Initiate a multipart upload in S3
     * 
     * @param fileName    Original file name
     * @param contentType MIME type of the file
     * @return MultipartInitResult containing Upload ID and S3 Key
     */
    public MultipartInitResult initiateMultipartUpload(String fileName, String contentType) {
        try {
            // Generate unique S3 key
            String s3Key = generateS3Key(fileName);

            CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType(contentType)
                    .build();

            CreateMultipartUploadResponse response = s3Client.createMultipartUpload(request);

            log.info("Initiated multipart upload for file: {} with uploadId: {}", fileName, response.uploadId());

            return new MultipartInitResult(response.uploadId(), s3Key);

        } catch (Exception e) {
            log.error("Failed to initiate multipart upload for file: {}", fileName, e);
            throw new S3UploadException("Failed to initiate multipart upload", e);
        }
    }

    /**
     * Upload a single part/chunk to S3
     * 
     * @param uploadId   S3 upload ID
     * @param s3Key      S3 object key
     * @param partNumber Part number (1-indexed)
     * @param data       Chunk data
     * @return ETag of the uploaded part
     */
    public String uploadPart(String uploadId, String s3Key, int partNumber, byte[] data) {
        try {
            UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .uploadId(uploadId)
                    .partNumber(partNumber)
                    .build();

            UploadPartResponse response = s3Client.uploadPart(
                    uploadPartRequest,
                    RequestBody.fromBytes(data));

            log.info("Uploaded part {} for uploadId: {}, ETag: {}", partNumber, uploadId, response.eTag());

            return response.eTag();

        } catch (Exception e) {
            log.error("Failed to upload part {} for uploadId: {}", partNumber, uploadId, e);
            throw new S3UploadException("Failed to upload part " + partNumber, e);
        }
    }

    /**
     * Complete the multipart upload
     * 
     * @param uploadId S3 upload ID
     * @param s3Key    S3 object key
     * @param parts    List of completed parts with their ETags
     * @return S3 object URL
     */
    public String completeMultipartUpload(String uploadId, String s3Key, List<CompletedPartInfo> parts) {
        try {
            // Convert our CompletedPartInfo to S3's CompletedPart
            List<CompletedPart> completedParts = parts.stream()
                    .map(part -> CompletedPart.builder()
                            .partNumber(part.getPartNumber())
                            .eTag(part.getETag())
                            .build())
                    .collect(Collectors.toList());

            CompletedMultipartUpload completedUpload = CompletedMultipartUpload.builder()
                    .parts(completedParts)
                    .build();

            CompleteMultipartUploadRequest request = CompleteMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .uploadId(uploadId)
                    .multipartUpload(completedUpload)
                    .build();

            CompleteMultipartUploadResponse response = s3Client.completeMultipartUpload(request);

            String fileUrl = String.format("https://%s.s3.amazonaws.com/%s", bucketName, s3Key);

            log.info("Completed multipart upload for uploadId: {}, URL: {}", uploadId, fileUrl);

            return fileUrl;

        } catch (Exception e) {
            log.error("Failed to complete multipart upload for uploadId: {}", uploadId, e);
            throw new S3UploadException("Failed to complete multipart upload", e);
        }
    }

    /**
     * Abort a multipart upload (cleanup on failure)
     * 
     * @param uploadId S3 upload ID
     * @param s3Key    S3 object key
     */
    public void abortMultipartUpload(String uploadId, String s3Key) {
        try {
            AbortMultipartUploadRequest request = AbortMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .uploadId(uploadId)
                    .build();

            s3Client.abortMultipartUpload(request);

            log.info("Aborted multipart upload for uploadId: {}", uploadId);

        } catch (Exception e) {
            log.error("Failed to abort multipart upload for uploadId: {}", uploadId, e);
            // Don't throw exception here, as this is cleanup logic
        }
    }

    /**
     * Generate a unique S3 key for the file
     * Format: uploads/{uuid}_{originalFileName}
     */
    private String generateS3Key(String fileName) {
        String uuid = UUID.randomUUID().toString();
        return String.format("uploads/%s_%s", uuid, fileName);
    }

    /**
     * Inner class to represent a completed part
     */
    public static class CompletedPartInfo {
        private final int partNumber;
        private final String eTag;

        public CompletedPartInfo(int partNumber, String eTag) {
            this.partNumber = partNumber;
            this.eTag = eTag;
        }

        public int getPartNumber() {
            return partNumber;
        }

        public String getETag() {
            return eTag;
        }
    }
}

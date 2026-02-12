package com.cloud.file.storage;

import com.cloud.file.config.S3Properties;
import com.cloud.file.exception.S3UploadFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.time.Duration;
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
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

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
                    .bucket(s3Properties.getBucket())
                    .key(s3Key)
                    .contentType(contentType)
                    .serverSideEncryption(ServerSideEncryption.AES256) // Enforce Encryption
                    .build();

            CreateMultipartUploadResponse response = s3Client.createMultipartUpload(request);

            log.info("Initiated multipart upload for file: {} with uploadId: {}", fileName, response.uploadId());

            return new MultipartInitResult(response.uploadId(), s3Key);

        } catch (Exception e) {
            log.error("Failed to initiate multipart upload for file: {}", fileName, e);
            throw new S3UploadFailedException("Failed to initiate multipart upload", e);
        }
    }

    public String uploadPart(String uploadId, String s3Key, int partNumber, byte[] data) {
        try {
            UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                    .bucket(s3Properties.getBucket())
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
            throw new S3UploadFailedException("Failed to upload part " + partNumber, e);
        }
    }

    public String completeMultipartUpload(String uploadId, String s3Key, List<CompletedPartInfo> parts) {
        try {
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
                    .bucket(s3Properties.getBucket())
                    .key(s3Key)
                    .uploadId(uploadId)
                    .multipartUpload(completedUpload)
                    .build();

            s3Client.completeMultipartUpload(request);

            // We don't return public URL anymore.
            // But for internal log/reference, we can format it.
            String fileUrl = String.format("s3://%s/%s", s3Properties.getBucket(), s3Key);

            log.info("Completed multipart upload for uploadId: {}, Path: {}", uploadId, fileUrl);

            return fileUrl;

        } catch (Exception e) {
            log.error("Failed to complete multipart upload for uploadId: {}", uploadId, e);
            throw new S3UploadFailedException("Failed to complete multipart upload", e);
        }
    }

    /**
     * Generate Presigned URL for Secure Download
     */
    public String generatePresignedUrl(String s3Key) {
        try {
            GetObjectRequest objectRequest = GetObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(s3Key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(s3Properties.getPresignedUrlExpirationMinutes())) // 10
                                                                                                            // Minutes
                                                                                                            // expiry
                    .getObjectRequest(objectRequest)
                    .build();

            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);

            return presignedRequest.url().toString();

        } catch (Exception e) {
            log.error("Failed to generate presigned URL for key: {}", s3Key, e);
            throw new S3UploadFailedException("Failed to generate download link", e);
        }
    }

    public void abortMultipartUpload(String uploadId, String s3Key) {
        try {
            AbortMultipartUploadRequest request = AbortMultipartUploadRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(s3Key)
                    .uploadId(uploadId)
                    .build();

            s3Client.abortMultipartUpload(request);
            log.info("Aborted multipart upload for uploadId: {}", uploadId);
        } catch (Exception e) {
            log.error("Failed to abort multipart upload for uploadId: {}", uploadId, e);
        }
    }

    private String generateS3Key(String fileName) {
        String uuid = UUID.randomUUID().toString();
        // Structure for better organization (optional)
        return String.format("uploads/%s/%s", uuid, fileName);
    }

    @lombok.Data
    public static class CompletedPartInfo {
        private final int partNumber;
        private final String eTag;
    }
}

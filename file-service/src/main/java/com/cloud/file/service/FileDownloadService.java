package com.cloud.file.service;

import com.cloud.file.client.MetadataClient;
import com.cloud.file.client.dto.FileMetadataResponse;
import com.cloud.file.exception.UnauthorizedAccessException;
import com.cloud.file.exception.UploadSessionNotFoundException;
import com.cloud.file.storage.S3MultipartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileDownloadService {

    private final MetadataClient metadataClient;
    private final S3MultipartService s3MultipartService;

    public String generateDownloadUrl(String fileIdStr, String userId) {
        Long fileId;
        try {
            fileId = Long.parseLong(fileIdStr);
        } catch (NumberFormatException e) {
            throw new UploadSessionNotFoundException("Invalid ID format: " + fileIdStr);
        }

        FileMetadataResponse metadata = metadataClient.getFile(fileId);

        // Strict Authorization Layer
        if (!metadata.getOwner().equals(userId)) {
            log.warn("Access Denied: User {} attempted to access file {} owned by {}", userId, fileId,
                    metadata.getOwner());
            throw new UnauthorizedAccessException("You are not authorized to access this file.");
        }

        // Generate Presigned URL
        return s3MultipartService.generatePresignedUrl(metadata.getS3Key());
    }
}

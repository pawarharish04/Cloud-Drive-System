package com.cloud.file.service;

import com.cloud.file.client.MetadataClient;
import com.cloud.file.client.dto.FileMetadataResponse;
import com.cloud.file.entity.UploadStatus;
import com.cloud.file.exception.UnauthorizedAccessException;
import com.cloud.file.storage.S3MultipartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Integration tests for security and authorization logic.
 * Tests validate:
 * - Cross-user access prevention (403)
 * - Owner validation
 * - Authorization enforcement
 */
@SpringBootTest
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired
    private FileDownloadService fileDownloadService;

    @MockBean
    private MetadataClient metadataClient;

    @MockBean
    private S3MultipartService s3MultipartService;

    @BeforeEach
    void setUp() {
        reset(metadataClient, s3MultipartService);
    }

    @Test
    @DisplayName("Should allow owner to download their file")
    void shouldAllowOwnerToDownload() {
        // Given
        String fileId = "1";
        String ownerId = "user123";

        FileMetadataResponse metadata = new FileMetadataResponse();
        metadata.setId(1L);
        metadata.setFileName("test-file.txt");
        metadata.setOwner(ownerId);
        metadata.setS3Key("uploads/test-key");
        metadata.setStatus(UploadStatus.COMPLETED);

        when(metadataClient.getFile(1L)).thenReturn(metadata);
        when(s3MultipartService.generatePresignedUrl("uploads/test-key"))
                .thenReturn("https://s3.amazonaws.com/presigned-url");

        // When
        String downloadUrl = fileDownloadService.generateDownloadUrl(fileId, ownerId);

        // Then
        assertThat(downloadUrl).isNotNull();
        assertThat(downloadUrl).contains("presigned-url");

        verify(metadataClient, times(1)).getFile(1L);
        verify(s3MultipartService, times(1)).generatePresignedUrl("uploads/test-key");
    }

    @Test
    @DisplayName("Should block non-owner from downloading file (403)")
    void shouldBlockNonOwnerFromDownloading() {
        // Given
        String fileId = "1";
        String ownerId = "user123";
        String attackerId = "attacker456";

        FileMetadataResponse metadata = new FileMetadataResponse();
        metadata.setId(1L);
        metadata.setFileName("test-file.txt");
        metadata.setOwner(ownerId); // File owned by user123
        metadata.setS3Key("uploads/test-key");
        metadata.setStatus(UploadStatus.COMPLETED);

        when(metadataClient.getFile(1L)).thenReturn(metadata);

        // When/Then - Attacker tries to download
        assertThatThrownBy(() -> fileDownloadService.generateDownloadUrl(fileId, attackerId))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("not authorized to access this file");

        // Verify presigned URL was NOT generated
        verify(s3MultipartService, never()).generatePresignedUrl(anyString());
    }

    @Test
    @DisplayName("Should validate file ownership before any operation")
    void shouldValidateFileOwnershipBeforeOperation() {
        // Given
        String fileId = "1";
        String userId = "user123";

        FileMetadataResponse metadata = new FileMetadataResponse();
        metadata.setId(1L);
        metadata.setOwner("different-user"); // Different owner

        when(metadataClient.getFile(1L)).thenReturn(metadata);

        // When/Then
        assertThatThrownBy(() -> fileDownloadService.generateDownloadUrl(fileId, userId))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    @DisplayName("Should handle invalid file ID format")
    void shouldHandleInvalidFileIdFormat() {
        // Given
        String invalidFileId = "not-a-number";
        String userId = "user123";

        // When/Then
        assertThatThrownBy(() -> fileDownloadService.generateDownloadUrl(invalidFileId, userId))
                .hasMessageContaining("Invalid ID format");
    }

    @Test
    @DisplayName("Should enforce authorization for all users")
    void shouldEnforceAuthorizationForAllUsers() {
        // Given - Multiple users trying to access same file
        String fileId = "1";
        String ownerId = "owner123";

        FileMetadataResponse metadata = new FileMetadataResponse();
        metadata.setId(1L);
        metadata.setOwner(ownerId);
        metadata.setS3Key("uploads/test-key");

        when(metadataClient.getFile(1L)).thenReturn(metadata);
        when(s3MultipartService.generatePresignedUrl(anyString()))
                .thenReturn("https://s3.amazonaws.com/presigned-url");

        // When - Owner accesses (should succeed)
        String ownerUrl = fileDownloadService.generateDownloadUrl(fileId, ownerId);
        assertThat(ownerUrl).isNotNull();

        // When - Non-owner accesses (should fail)
        assertThatThrownBy(() -> fileDownloadService.generateDownloadUrl(fileId, "attacker1"))
                .isInstanceOf(UnauthorizedAccessException.class);

        assertThatThrownBy(() -> fileDownloadService.generateDownloadUrl(fileId, "attacker2"))
                .isInstanceOf(UnauthorizedAccessException.class);

        // Verify presigned URL was only generated once (for owner)
        verify(s3MultipartService, times(1)).generatePresignedUrl(anyString());
    }
}

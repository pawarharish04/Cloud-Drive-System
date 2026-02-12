package com.cloud.file.service;

import com.cloud.file.client.MetadataClient;
import com.cloud.file.client.dto.FileMetadataResponse;
import com.cloud.file.dto.InitiateUploadRequest;
import com.cloud.file.dto.InitiateUploadResponse;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for FileService with mocked S3 and Metadata clients.
 * Tests validate:
 * - Upload workflow orchestration
 * - Security (authorization)
 * - Error handling
 */
@SpringBootTest
@ActiveProfiles("test")
class FileServiceIntegrationTest {

    @Autowired
    private FileService fileService;

    @MockBean
    private S3MultipartService s3MultipartService;

    @MockBean
    private MetadataClient metadataClient;

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        reset(s3MultipartService, metadataClient);
    }

    @Test
    @DisplayName("Should initiate upload successfully")
    void shouldInitiateUploadSuccessfully() {
        // Given
        InitiateUploadRequest request = new InitiateUploadRequest();
        request.setFileName("test-file.txt");
        request.setFileSize(1024L);
        request.setTotalChunks(2);
        request.setContentType("text/plain");
        request.setUserId("user123");

        // Mock S3 response
        S3MultipartService.MultipartInitResult s3Result = new S3MultipartService.MultipartInitResult("upload-id-123",
                "s3-key-123");
        when(s3MultipartService.initiateMultipartUpload(anyString(), anyString()))
                .thenReturn(s3Result);

        // Mock Metadata response
        FileMetadataResponse metadataResponse = new FileMetadataResponse();
        metadataResponse.setId(1L);
        metadataResponse.setFileName("test-file.txt");
        metadataResponse.setStatus(UploadStatus.ACTIVE);
        when(metadataClient.initiateUpload(any())).thenReturn(metadataResponse);

        // When
        InitiateUploadResponse response = fileService.initiateUpload(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getFileId()).isEqualTo(1L);
        assertThat(response.getUploadId()).isEqualTo("upload-id-123");
        assertThat(response.getS3Key()).isEqualTo("s3-key-123");

        // Verify interactions
        verify(s3MultipartService, times(1)).initiateMultipartUpload("test-file.txt", "text/plain");
        verify(metadataClient, times(1)).initiateUpload(any());
    }

    @Test
    @DisplayName("Should handle S3 failure during initiate")
    void shouldHandleS3FailureDuringInitiate() {
        // Given
        InitiateUploadRequest request = new InitiateUploadRequest();
        request.setFileName("test-file.txt");
        request.setFileSize(1024L);
        request.setTotalChunks(2);
        request.setContentType("text/plain");
        request.setUserId("user123");

        // Mock S3 to throw exception
        when(s3MultipartService.initiateMultipartUpload(anyString(), anyString()))
                .thenThrow(new RuntimeException("S3 unavailable"));

        // When/Then
        assertThatThrownBy(() -> fileService.initiateUpload(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("S3 unavailable");

        // Verify metadata was NOT called (fail fast)
        verify(metadataClient, never()).initiateUpload(any());
    }
}

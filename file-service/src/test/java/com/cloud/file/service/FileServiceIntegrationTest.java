package com.cloud.file.service;

import com.cloud.file.client.MetadataClient;
import com.cloud.file.client.dto.MetadataInitiateRequest;
import com.cloud.file.dto.InitiateUploadRequest;
import com.cloud.file.dto.InitiateUploadResponse;
import com.cloud.file.exception.S3UploadFailedException;
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

@SpringBootTest
@ActiveProfiles("test")
class FileServiceIntegrationTest {

    @Autowired
    private ChunkUploadService chunkUploadService;

    @MockBean
    private S3MultipartService s3MultipartService;

    @MockBean
    private MetadataClient metadataClient;

    @BeforeEach
    void setUp() {
        reset(s3MultipartService, metadataClient);
    }

    @Test
    @DisplayName("Should initiate upload successfully")
    void shouldInitiateUploadSuccessfully() {
        // Given
        InitiateUploadRequest request = new InitiateUploadRequest();
        request.setFileName("test-file.txt");
        request.setFileSize(1024L);
        request.setContentType("text/plain");
        request.setOwner("user123");

        // Mock S3 response
        S3MultipartService.MultipartInitResult s3Result = new S3MultipartService.MultipartInitResult("upload-id-123",
                "s3-key-123");
        when(s3MultipartService.initiateMultipartUpload(anyString(), anyString()))
                .thenReturn(s3Result);

        // Mock Metadata response
        Long expectedFileId = 1L;
        when(metadataClient.initiateSession(any(MetadataInitiateRequest.class)))
                .thenReturn(expectedFileId);

        // When
        InitiateUploadResponse response = chunkUploadService.initiateUpload(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getFileId()).isEqualTo("1");
        assertThat(response.getUploadId()).isEqualTo("upload-id-123");
        // S3 Key is not exposed in response

        // Verify interactions
        verify(s3MultipartService, times(1)).initiateMultipartUpload("test-file.txt", "text/plain");
        verify(metadataClient, times(1)).initiateSession(any(MetadataInitiateRequest.class));
    }

    @Test
    @DisplayName("Should handle S3 failure during initiate")
    void shouldHandleS3FailureDuringInitiate() {
        // Given
        InitiateUploadRequest request = new InitiateUploadRequest();
        request.setFileName("test-file.txt");
        request.setFileSize(1024L);
        request.setContentType("text/plain");
        request.setOwner("user123");

        // Mock S3 to throw exception
        when(s3MultipartService.initiateMultipartUpload(anyString(), anyString()))
                .thenThrow(new S3UploadFailedException("S3 unavailable", new RuntimeException()));

        // When/Then
        assertThatThrownBy(() -> chunkUploadService.initiateUpload(request))
                .isInstanceOf(S3UploadFailedException.class)
                .hasMessageContaining("S3 unavailable");

        // Verify metadata was NOT called (fail fast)
        verify(metadataClient, never()).initiateSession(any());
    }
}

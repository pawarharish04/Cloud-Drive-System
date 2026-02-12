package com.cloud.metadata.service;

import com.cloud.metadata.TestcontainersConfiguration;
import com.cloud.metadata.dto.ChunkUploadRequest;
import com.cloud.metadata.dto.FileMetadataResponse;
import com.cloud.metadata.dto.InitiateUploadRequest;
import com.cloud.metadata.entity.ChunkMetadata;
import com.cloud.metadata.entity.FileMetadata;
import com.cloud.metadata.entity.UploadStatus;
import com.cloud.metadata.exception.ChunkAlreadyExistsException;
import com.cloud.metadata.exception.FileNotFoundException;
import com.cloud.metadata.exception.InvalidStateTransitionException;
import com.cloud.metadata.repository.ChunkMetadataRepository;
import com.cloud.metadata.repository.FileMetadataRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for MetadataService using Testcontainers PostgreSQL.
 * Tests validate:
 * - Full upload lifecycle (initiate → chunk → complete)
 * - State transitions
 * - Idempotency
 * - Data integrity
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class MetadataServiceIntegrationTest {

    @Autowired
    private MetadataService metadataService;

    @Autowired
    private FileMetadataRepository fileMetadataRepository;

    @Autowired
    private ChunkMetadataRepository chunkMetadataRepository;

    @BeforeEach
    void setUp() {
        // Clean database before each test for isolation
        chunkMetadataRepository.deleteAll();
        fileMetadataRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        // Clean up after each test
        chunkMetadataRepository.deleteAll();
        fileMetadataRepository.deleteAll();
    }

    @Test
    @DisplayName("Should initiate upload session successfully")
    void shouldInitiateUploadSession() {
        // Given
        InitiateUploadRequest request = new InitiateUploadRequest();
        request.setFileName("test-file.txt");
        request.setFileSize(1024L);
        request.setTotalChunks(2);
        request.setS3Key("uploads/test-key");
        request.setUploadId("test-upload-id");
        request.setOwner("user123");

        // When
        FileMetadataResponse response = metadataService.initiateSession(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getFileName()).isEqualTo("test-file.txt");
        assertThat(response.getStatus()).isEqualTo(UploadStatus.ACTIVE);
        assertThat(response.getOwner()).isEqualTo("user123");

        // Verify database state
        FileMetadata savedFile = fileMetadataRepository.findById(response.getId()).orElseThrow();
        assertThat(savedFile.getStatus()).isEqualTo(UploadStatus.ACTIVE);
        assertThat(savedFile.getTotalChunks()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should add chunk successfully")
    void shouldAddChunkSuccessfully() {
        // Given - Initiate session first
        FileMetadata file = createActiveSession("test-file.txt", 2);

        ChunkUploadRequest chunkRequest = new ChunkUploadRequest();
        chunkRequest.setFileId(file.getId());
        chunkRequest.setChunkNumber(1);
        chunkRequest.setEtag("etag-123");

        // When
        metadataService.addChunk(chunkRequest);

        // Then
        List<ChunkMetadata> chunks = chunkMetadataRepository.findByFileIdOrderByChunkNumber(file.getId());
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getChunkNumber()).isEqualTo(1);
        assertThat(chunks.get(0).getEtag()).isEqualTo("etag-123");
    }

    @Test
    @DisplayName("Should handle duplicate chunk upload idempotently")
    void shouldHandleDuplicateChunkIdempotently() {
        // Given - Session with one chunk already uploaded
        FileMetadata file = createActiveSession("test-file.txt", 2);
        ChunkUploadRequest chunkRequest = new ChunkUploadRequest();
        chunkRequest.setFileId(file.getId());
        chunkRequest.setChunkNumber(1);
        chunkRequest.setEtag("etag-123");

        metadataService.addChunk(chunkRequest);

        // When - Upload same chunk again
        metadataService.addChunk(chunkRequest);

        // Then - Should not create duplicate
        List<ChunkMetadata> chunks = chunkMetadataRepository.findByFileIdOrderByChunkNumber(file.getId());
        assertThat(chunks).hasSize(1); // Still only 1 chunk
        assertThat(chunks.get(0).getEtag()).isEqualTo("etag-123");
    }

    @Test
    @DisplayName("Should get uploaded chunks in correct order")
    void shouldGetUploadedChunksInOrder() {
        // Given - Session with multiple chunks
        FileMetadata file = createActiveSession("test-file.txt", 3);

        // Upload chunks out of order
        addChunk(file.getId(), 3, "etag-3");
        addChunk(file.getId(), 1, "etag-1");
        addChunk(file.getId(), 2, "etag-2");

        // When
        List<ChunkMetadata> chunks = metadataService.getUploadedChunks(file.getId());

        // Then - Should be ordered by chunk number
        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).getChunkNumber()).isEqualTo(1);
        assertThat(chunks.get(1).getChunkNumber()).isEqualTo(2);
        assertThat(chunks.get(2).getChunkNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should complete session successfully when all chunks uploaded")
    void shouldCompleteSessionSuccessfully() {
        // Given - Session with all chunks uploaded
        FileMetadata file = createActiveSession("test-file.txt", 2);
        addChunk(file.getId(), 1, "etag-1");
        addChunk(file.getId(), 2, "etag-2");

        // When
        metadataService.completeSession(file.getId(), "s3://bucket/key");

        // Then
        FileMetadata completedFile = fileMetadataRepository.findById(file.getId()).orElseThrow();
        assertThat(completedFile.getStatus()).isEqualTo(UploadStatus.COMPLETED);
        assertThat(completedFile.getS3Url()).isEqualTo("s3://bucket/key");
    }

    @Test
    @DisplayName("Should fail to complete session with missing chunks")
    void shouldFailToCompleteSessionWithMissingChunks() {
        // Given - Session with only 1 of 2 chunks
        FileMetadata file = createActiveSession("test-file.txt", 2);
        addChunk(file.getId(), 1, "etag-1");
        // Chunk 2 is missing

        // When/Then
        assertThatThrownBy(() -> metadataService.completeSession(file.getId(), "s3://bucket/key"))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Not all chunks uploaded");
    }

    @Test
    @DisplayName("Should prevent invalid state transition from COMPLETED to ACTIVE")
    void shouldPreventInvalidStateTransition() {
        // Given - Completed session
        FileMetadata file = createActiveSession("test-file.txt", 1);
        addChunk(file.getId(), 1, "etag-1");
        metadataService.completeSession(file.getId(), "s3://bucket/key");

        // When/Then - Try to add chunk to completed session
        ChunkUploadRequest chunkRequest = new ChunkUploadRequest();
        chunkRequest.setFileId(file.getId());
        chunkRequest.setChunkNumber(2);
        chunkRequest.setEtag("etag-2");

        assertThatThrownBy(() -> metadataService.addChunk(chunkRequest))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Cannot add chunk to completed upload");
    }

    @Test
    @DisplayName("Should handle complete idempotently")
    void shouldHandleCompleteIdempotently() {
        // Given - Completed session
        FileMetadata file = createActiveSession("test-file.txt", 1);
        addChunk(file.getId(), 1, "etag-1");
        metadataService.completeSession(file.getId(), "s3://bucket/key");

        // When - Complete again
        metadataService.completeSession(file.getId(), "s3://bucket/key");

        // Then - Should still be completed (no error)
        FileMetadata completedFile = fileMetadataRepository.findById(file.getId()).orElseThrow();
        assertThat(completedFile.getStatus()).isEqualTo(UploadStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should throw exception when file not found")
    void shouldThrowExceptionWhenFileNotFound() {
        // When/Then
        assertThatThrownBy(() -> metadataService.getFile(999L))
                .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    @DisplayName("Should retrieve file by ID successfully")
    void shouldRetrieveFileById() {
        // Given
        FileMetadata file = createActiveSession("test-file.txt", 2);

        // When
        FileMetadataResponse response = metadataService.getFile(file.getId());

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(file.getId());
        assertThat(response.getFileName()).isEqualTo("test-file.txt");
        assertThat(response.getStatus()).isEqualTo(UploadStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should validate transaction rollback on error")
    void shouldRollbackTransactionOnError() {
        // Given - This test validates that if an error occurs, the transaction rolls
        // back
        // We'll simulate this by trying to add a chunk to a non-existent file

        ChunkUploadRequest chunkRequest = new ChunkUploadRequest();
        chunkRequest.setFileId(999L); // Non-existent file
        chunkRequest.setChunkNumber(1);
        chunkRequest.setEtag("etag-123");

        // When/Then
        assertThatThrownBy(() -> metadataService.addChunk(chunkRequest))
                .isInstanceOf(FileNotFoundException.class);

        // Verify no orphaned chunk was created
        List<ChunkMetadata> chunks = chunkMetadataRepository.findAll();
        assertThat(chunks).isEmpty();
    }

    // Helper methods

    private FileMetadata createActiveSession(String fileName, int totalChunks) {
        InitiateUploadRequest request = new InitiateUploadRequest();
        request.setFileName(fileName);
        request.setFileSize(1024L * totalChunks);
        request.setTotalChunks(totalChunks);
        request.setS3Key("uploads/test-key-" + System.currentTimeMillis());
        request.setUploadId("upload-id-" + System.currentTimeMillis());
        request.setOwner("testuser");

        FileMetadataResponse response = metadataService.initiateSession(request);
        return fileMetadataRepository.findById(response.getId()).orElseThrow();
    }

    private void addChunk(Long fileId, int chunkNumber, String etag) {
        ChunkUploadRequest request = new ChunkUploadRequest();
        request.setFileId(fileId);
        request.setChunkNumber(chunkNumber);
        request.setEtag(etag);
        metadataService.addChunk(request);
    }
}

package com.cloud.metadata.service;

import com.cloud.metadata.TestcontainersConfiguration;
import com.cloud.metadata.dto.ChunkUploadRequest;
import com.cloud.metadata.dto.FileMetadataResponse;
import com.cloud.metadata.dto.InitiateUploadRequest;
import com.cloud.metadata.entity.ChunkMetadata;
import com.cloud.metadata.entity.FileMetadata;
import com.cloud.metadata.entity.UploadStatus;
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
 * Full upload flow integration test.
 * Simulates complete upload lifecycle: initiate → upload chunks → complete.
 * Validates data integrity and state consistency.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class UploadFlowIntegrationTest {

    @Autowired
    private MetadataService metadataService;

    @Autowired
    private FileMetadataRepository fileMetadataRepository;

    @Autowired
    private ChunkMetadataRepository chunkMetadataRepository;

    @BeforeEach
    void setUp() {
        chunkMetadataRepository.deleteAll();
        fileMetadataRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        chunkMetadataRepository.deleteAll();
        fileMetadataRepository.deleteAll();
    }

    @Test
    @DisplayName("Full upload flow: initiate → upload 3 chunks → complete")
    void fullUploadFlowSuccess() {
        // Step 1: Initiate upload
        InitiateUploadRequest initiateRequest = new InitiateUploadRequest();
        initiateRequest.setFileName("large-file.bin");
        initiateRequest.setFileSize(15728640L); // 15MB
        initiateRequest.setTotalChunks(3);
        initiateRequest.setS3Key("uploads/large-file-key");
        initiateRequest.setUploadId("upload-123");
        initiateRequest.setOwner("user456");

        FileMetadataResponse initiateResponse = metadataService.initiateSession(initiateRequest);

        assertThat(initiateResponse).isNotNull();
        assertThat(initiateResponse.getStatus()).isEqualTo(UploadStatus.ACTIVE);
        Long fileId = initiateResponse.getId();

        // Step 2: Upload chunks (simulate out-of-order upload)
        uploadChunk(fileId, 2, "etag-chunk-2");
        uploadChunk(fileId, 1, "etag-chunk-1");
        uploadChunk(fileId, 3, "etag-chunk-3");

        // Verify chunks are saved
        List<ChunkMetadata> chunks = chunkMetadataRepository.findByFileIdOrderByChunkNumber(fileId);
        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).getChunkNumber()).isEqualTo(1);
        assertThat(chunks.get(1).getChunkNumber()).isEqualTo(2);
        assertThat(chunks.get(2).getChunkNumber()).isEqualTo(3);

        // Step 3: Complete upload
        metadataService.completeSession(fileId, "s3://bucket/uploads/large-file-key");

        // Step 4: Verify final state
        FileMetadata completedFile = fileMetadataRepository.findById(fileId).orElseThrow();
        assertThat(completedFile.getStatus()).isEqualTo(UploadStatus.COMPLETED);
        assertThat(completedFile.getS3Url()).isEqualTo("s3://bucket/uploads/large-file-key");
        assertThat(completedFile.getFileName()).isEqualTo("large-file.bin");
        assertThat(completedFile.getOwner()).isEqualTo("user456");

        // Verify no duplicate chunks
        List<ChunkMetadata> finalChunks = chunkMetadataRepository.findByFileIdOrderByChunkNumber(fileId);
        assertThat(finalChunks).hasSize(3);
    }

    @Test
    @DisplayName("Multiple concurrent uploads should not interfere")
    void multipleConcurrentUploads() {
        // Upload 1
        FileMetadataResponse file1 = initiateUpload("file1.txt", 2, "user1");
        uploadChunk(file1.getId(), 1, "file1-chunk1-etag");
        uploadChunk(file1.getId(), 2, "file1-chunk2-etag");

        // Upload 2 (different user)
        FileMetadataResponse file2 = initiateUpload("file2.txt", 2, "user2");
        uploadChunk(file2.getId(), 1, "file2-chunk1-etag");
        uploadChunk(file2.getId(), 2, "file2-chunk2-etag");

        // Complete both
        metadataService.completeSession(file1.getId(), "s3://bucket/file1");
        metadataService.completeSession(file2.getId(), "s3://bucket/file2");

        // Verify both completed independently
        FileMetadata completedFile1 = fileMetadataRepository.findById(file1.getId()).orElseThrow();
        FileMetadata completedFile2 = fileMetadataRepository.findById(file2.getId()).orElseThrow();

        assertThat(completedFile1.getStatus()).isEqualTo(UploadStatus.COMPLETED);
        assertThat(completedFile2.getStatus()).isEqualTo(UploadStatus.COMPLETED);
        assertThat(completedFile1.getOwner()).isEqualTo("user1");
        assertThat(completedFile2.getOwner()).isEqualTo("user2");

        // Verify chunks are isolated
        List<ChunkMetadata> file1Chunks = chunkMetadataRepository.findByFileIdOrderByChunkNumber(file1.getId());
        List<ChunkMetadata> file2Chunks = chunkMetadataRepository.findByFileIdOrderByChunkNumber(file2.getId());

        assertThat(file1Chunks).hasSize(2);
        assertThat(file2Chunks).hasSize(2);
        assertThat(file1Chunks.get(0).getEtag()).contains("file1");
        assertThat(file2Chunks.get(0).getEtag()).contains("file2");
    }

    @Test
    @DisplayName("Retry scenario: duplicate chunk upload during flow")
    void retryScenarioDuplicateChunk() {
        // Initiate
        FileMetadataResponse file = initiateUpload("retry-test.txt", 3, "user123");

        // Upload chunk 1
        uploadChunk(file.getId(), 1, "etag-1");

        // Upload chunk 2
        uploadChunk(file.getId(), 2, "etag-2");

        // Retry chunk 2 (simulate network retry)
        uploadChunk(file.getId(), 2, "etag-2");

        // Upload chunk 3
        uploadChunk(file.getId(), 3, "etag-3");

        // Verify only 3 chunks (no duplicate)
        List<ChunkMetadata> chunks = chunkMetadataRepository.findByFileIdOrderByChunkNumber(file.getId());
        assertThat(chunks).hasSize(3);

        // Complete
        metadataService.completeSession(file.getId(), "s3://bucket/retry-test");

        FileMetadata completedFile = fileMetadataRepository.findById(file.getId()).orElseThrow();
        assertThat(completedFile.getStatus()).isEqualTo(UploadStatus.COMPLETED);
    }

    @Test
    @DisplayName("Idempotent complete: calling complete twice should succeed")
    void idempotentComplete() {
        // Initiate and upload
        FileMetadataResponse file = initiateUpload("idempotent-test.txt", 1, "user123");
        uploadChunk(file.getId(), 1, "etag-1");

        // Complete first time
        metadataService.completeSession(file.getId(), "s3://bucket/idempotent-test");

        FileMetadata completedFile = fileMetadataRepository.findById(file.getId()).orElseThrow();
        assertThat(completedFile.getStatus()).isEqualTo(UploadStatus.COMPLETED);

        // Complete second time (idempotency test)
        metadataService.completeSession(file.getId(), "s3://bucket/idempotent-test");

        // Verify still completed (no error)
        completedFile = fileMetadataRepository.findById(file.getId()).orElseThrow();
        assertThat(completedFile.getStatus()).isEqualTo(UploadStatus.COMPLETED);
    }

    @Test
    @DisplayName("Large file upload: 10 chunks")
    void largeFileUpload() {
        // Initiate large upload
        FileMetadataResponse file = initiateUpload("large-file.bin", 10, "user123");

        // Upload 10 chunks
        for (int i = 1; i <= 10; i++) {
            uploadChunk(file.getId(), i, "etag-chunk-" + i);
        }

        // Verify all chunks saved
        List<ChunkMetadata> chunks = chunkMetadataRepository.findByFileIdOrderByChunkNumber(file.getId());
        assertThat(chunks).hasSize(10);

        // Complete
        metadataService.completeSession(file.getId(), "s3://bucket/large-file");

        FileMetadata completedFile = fileMetadataRepository.findById(file.getId()).orElseThrow();
        assertThat(completedFile.getStatus()).isEqualTo(UploadStatus.COMPLETED);
        assertThat(completedFile.getTotalChunks()).isEqualTo(10);
    }

    // Helper methods

    private FileMetadataResponse initiateUpload(String fileName, int totalChunks, String owner) {
        InitiateUploadRequest request = new InitiateUploadRequest();
        request.setFileName(fileName);
        request.setFileSize(1024L * totalChunks);
        request.setTotalChunks(totalChunks);
        request.setS3Key("uploads/" + fileName);
        request.setUploadId("upload-" + System.currentTimeMillis());
        request.setOwner(owner);

        return metadataService.initiateSession(request);
    }

    private void uploadChunk(Long fileId, int chunkNumber, String etag) {
        ChunkUploadRequest request = new ChunkUploadRequest();
        request.setFileId(fileId);
        request.setChunkNumber(chunkNumber);
        request.setEtag(etag);
        metadataService.addChunk(request);
    }
}

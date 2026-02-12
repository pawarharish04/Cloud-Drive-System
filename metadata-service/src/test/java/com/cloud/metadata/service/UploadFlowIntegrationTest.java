package com.cloud.metadata.service;

import com.cloud.metadata.TestcontainersConfiguration;
import com.cloud.metadata.dto.FileMetadataResponse;
import com.cloud.metadata.entity.ChunkMetadata;
import com.cloud.metadata.entity.FileMetadata;
import com.cloud.metadata.enums.UploadStatus;
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
        String fileName = "large-file.bin";
        Long size = 15728640L; // 15MB
        int totalChunks = 3;
        String userId = "user456";

        Long fileId = metadataService.initiateSession(fileName, userId, "upload-123", totalChunks, size,
                "application/octet-stream");

        // Verify PENDING state initially
        FileMetadata file = fileMetadataRepository.findById(fileId).orElseThrow();
        assertThat(file.getStatus()).isEqualTo(UploadStatus.PENDING);

        // Step 2: Upload chunks (simulate out-of-order upload)
        metadataService.addChunk(fileId, 2, "etag-chunk-2", 5242880L);
        metadataService.addChunk(fileId, 1, "etag-chunk-1", 5242880L);
        metadataService.addChunk(fileId, 3, "etag-chunk-3", 5242880L);

        // Verify chunks are saved
        List<ChunkMetadata> chunks = chunkMetadataRepository.findByFileMetadataIdOrderByChunkNumberAsc(fileId);
        assertThat(chunks).hasSize(3);
        assertThat(chunks).extracting("chunkNumber").containsExactly(1, 2, 3);

        // Verify state is ACTIVE
        file = fileMetadataRepository.findById(fileId).orElseThrow();
        assertThat(file.getStatus()).isEqualTo(UploadStatus.ACTIVE);

        // Step 3: Complete upload
        metadataService.completeSession(fileId);

        // Step 4: Verify final state
        FileMetadata completedFile = fileMetadataRepository.findById(fileId).orElseThrow();
        assertThat(completedFile.getStatus()).isEqualTo(UploadStatus.COMPLETED);
        assertThat(completedFile.getFileName()).isEqualTo("large-file.bin");
        assertThat(completedFile.getOwner()).isEqualTo("user456");

        // Verify no duplicate chunks
        List<ChunkMetadata> finalChunks = chunkMetadataRepository.findByFileMetadataIdOrderByChunkNumberAsc(fileId);
        assertThat(finalChunks).hasSize(3);
    }

    @Test
    @DisplayName("Multiple concurrent uploads should not interfere")
    void multipleConcurrentUploads() {
        // Upload 1
        Long fileId1 = initiateUpload("file1.txt", 2, "user1");
        metadataService.addChunk(fileId1, 1, "file1-chunk1-etag", 1024L);
        metadataService.addChunk(fileId1, 2, "file1-chunk2-etag", 1024L);

        // Upload 2 (different user)
        Long fileId2 = initiateUpload("file2.txt", 2, "user2");
        metadataService.addChunk(fileId2, 1, "file2-chunk1-etag", 1024L);
        metadataService.addChunk(fileId2, 2, "file2-chunk2-etag", 1024L);

        // Complete both
        metadataService.completeSession(fileId1);
        metadataService.completeSession(fileId2);

        // Verify both completed independently
        FileMetadata completedFile1 = fileMetadataRepository.findById(fileId1).orElseThrow();
        FileMetadata completedFile2 = fileMetadataRepository.findById(fileId2).orElseThrow();

        assertThat(completedFile1.getStatus()).isEqualTo(UploadStatus.COMPLETED);
        assertThat(completedFile2.getStatus()).isEqualTo(UploadStatus.COMPLETED);
        assertThat(completedFile1.getOwner()).isEqualTo("user1");
        assertThat(completedFile2.getOwner()).isEqualTo("user2");

        // Verify chunks are isolated
        List<ChunkMetadata> file1Chunks = chunkMetadataRepository.findByFileMetadataIdOrderByChunkNumberAsc(fileId1);
        List<ChunkMetadata> file2Chunks = chunkMetadataRepository.findByFileMetadataIdOrderByChunkNumberAsc(fileId2);

        assertThat(file1Chunks).hasSize(2);
        assertThat(file2Chunks).hasSize(2);
        assertThat(file1Chunks.get(0).getEtag()).contains("file1");
        assertThat(file2Chunks.get(0).getEtag()).contains("file2");
    }

    @Test
    @DisplayName("Retry scenario: duplicate chunk upload during flow")
    void retryScenarioDuplicateChunk() {
        // Initiate
        Long fileId = initiateUpload("retry-test.txt", 3, "user123");

        // Upload chunk 1
        metadataService.addChunk(fileId, 1, "etag-1", 1024L);

        // Upload chunk 2
        metadataService.addChunk(fileId, 2, "etag-2", 1024L);

        // Retry chunk 2 (simulate network retry)
        metadataService.addChunk(fileId, 2, "etag-2", 1024L);

        // Upload chunk 3
        metadataService.addChunk(fileId, 3, "etag-3", 1024L);

        // Verify only 3 chunks (no duplicate)
        List<ChunkMetadata> chunks = chunkMetadataRepository.findByFileMetadataIdOrderByChunkNumberAsc(fileId);
        assertThat(chunks).hasSize(3);

        // Complete
        metadataService.completeSession(fileId);

        FileMetadata completedFile = fileMetadataRepository.findById(fileId).orElseThrow();
        assertThat(completedFile.getStatus()).isEqualTo(UploadStatus.COMPLETED);
    }

    @Test
    @DisplayName("Idempotent complete: calling complete twice should succeed")
    void idempotentComplete() {
        // Initiate and upload
        Long fileId = initiateUpload("idempotent-test.txt", 1, "user123");
        metadataService.addChunk(fileId, 1, "etag-1", 1024L);

        // Complete first time
        metadataService.completeSession(fileId);

        FileMetadata completedFile = fileMetadataRepository.findById(fileId).orElseThrow();
        assertThat(completedFile.getStatus()).isEqualTo(UploadStatus.COMPLETED);

        // Complete second time (idempotency test)
        metadataService.completeSession(fileId);

        // Verify still completed (no error, log message only)
        completedFile = fileMetadataRepository.findById(fileId).orElseThrow();
        assertThat(completedFile.getStatus()).isEqualTo(UploadStatus.COMPLETED);
    }

    // Helper methods

    private Long initiateUpload(String fileName, int totalChunks, String owner) {
        return metadataService.initiateSession(
                fileName,
                owner,
                "upload-" + System.nanoTime(),
                totalChunks,
                1024L * totalChunks,
                "text/plain");
    }
}

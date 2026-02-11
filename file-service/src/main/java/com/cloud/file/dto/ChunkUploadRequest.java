package com.cloud.file.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChunkUploadRequest {

    @NotBlank(message = "File ID is required")
    private String fileId; // Changed from uploadId to fileId (as String to support potential future UUIDs,
                           // though Metadata uses Long)
    // Wait, Metadata uses Long. Let's make it String in DTO but parse it,
    // or better, make it String in DTO to matching Metadata Service DTOs if they
    // use String IDs?
    // Metadata uses Long. Let's stick to String to avoid breaking clients who
    // expect strings, and parse.
    // Or just change to Long. Let's keep String for flexibility and consistency
    // with uploadId,
    // but name it fileId.

    @NotNull(message = "Chunk number is required")
    @Positive(message = "Chunk number must be positive")
    private Integer chunkNumber;

    @NotNull(message = "Chunk data is required")
    private byte[] chunkData;

    private String checksum; // Optional: for chunk validation
}

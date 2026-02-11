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

    @NotBlank(message = "Upload ID is required")
    private String uploadId;

    @NotNull(message = "Chunk number is required")
    @Positive(message = "Chunk number must be positive")
    private Integer chunkNumber;

    @NotNull(message = "Chunk data is required")
    private byte[] chunkData;

    private String checksum; // Optional: for chunk validation
}

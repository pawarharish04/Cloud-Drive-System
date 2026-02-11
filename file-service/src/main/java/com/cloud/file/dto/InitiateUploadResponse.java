package com.cloud.file.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InitiateUploadResponse {

    private String uploadId;
    private String fileName;
    private Long fileSize;
    private Integer chunkSize;
    private Integer totalChunks;
    private String message;
}

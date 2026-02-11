package com.cloud.file.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChunkUploadResponse {

    private String uploadId;
    private Integer chunkNumber;
    private String etag;
    private Long chunkSize;
    private String status;
    private String message;
}

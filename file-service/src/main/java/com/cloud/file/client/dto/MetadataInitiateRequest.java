package com.cloud.file.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MetadataInitiateRequest {
    private String fileName;
    private String userId;
    private String uploadId;
    private String s3Key;
    private Integer totalChunks;
    private Long size;
    private String contentType;
}

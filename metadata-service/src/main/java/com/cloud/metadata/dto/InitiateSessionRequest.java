package com.cloud.metadata.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InitiateSessionRequest {
    private String fileName;
    private String userId;
    private String uploadId;
    private Integer totalChunks;
    private Long size;
    private String contentType;
}

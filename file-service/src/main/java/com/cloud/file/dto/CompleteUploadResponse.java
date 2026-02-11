package com.cloud.file.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CompleteUploadResponse {

    private String fileId;
    private String fileName;
    private String fileUrl;
    private Long fileSize;
    private Integer totalChunks;
    private String status;
    private String message;
}

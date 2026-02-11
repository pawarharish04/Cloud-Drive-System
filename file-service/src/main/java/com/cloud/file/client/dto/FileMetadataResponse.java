package com.cloud.file.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FileMetadataResponse {
    private Long id;
    private String fileName;
    private String fileType;
    private Long size;
    private String s3Key;
    private String owner;
    private String uploadId;
    private String status;
    private Integer totalChunks;
    private LocalDateTime uploadedAt;
}

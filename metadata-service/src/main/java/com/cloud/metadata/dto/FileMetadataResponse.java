package com.cloud.metadata.dto;

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
    private long size;
    private String fileType;
    private String s3Key;
    private String owner;
    private LocalDateTime uploadedAt;
}

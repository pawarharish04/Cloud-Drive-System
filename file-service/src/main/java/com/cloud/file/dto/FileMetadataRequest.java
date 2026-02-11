package com.cloud.file.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileMetadataRequest {
    private String fileName;
    private String fileType;
    private long size;
    private String s3Key;
    private String owner;
}

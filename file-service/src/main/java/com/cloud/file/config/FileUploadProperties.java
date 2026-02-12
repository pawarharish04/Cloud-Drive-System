package com.cloud.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "file.upload")
@Data
public class FileUploadProperties {

    /**
     * Default chunk size in bytes (5MB)
     */
    private long chunkSize = 5242880;

    /**
     * Maximum file size in bytes (5GB)
     */
    private long maxFileSize = 5368709120L;

    /**
     * Maximum chunk size in bytes (100MB)
     */
    private long maxChunkSize = 104857600;

    /**
     * Upload session timeout in hours
     */
    private int sessionTimeoutHours = 24;
}

package com.cloud.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "cloud.aws.s3")
@Data
public class S3Properties {

    /**
     * S3 bucket name
     */
    private String bucket;

    /**
     * AWS region
     */
    private String region;

    /**
     * Presigned URL expiration in minutes
     */
    private int presignedUrlExpirationMinutes = 10;
}

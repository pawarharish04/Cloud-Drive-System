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
public class MetadataChunkResponse {
    private Long id;
    private Integer chunkNumber;
    private String etag;
    private Long size;
    private LocalDateTime createdAt;
}

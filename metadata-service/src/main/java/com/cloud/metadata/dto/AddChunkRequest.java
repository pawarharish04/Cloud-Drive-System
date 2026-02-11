package com.cloud.metadata.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AddChunkRequest {
    private Integer chunkNumber;
    private String etag;
    private Long size;
}

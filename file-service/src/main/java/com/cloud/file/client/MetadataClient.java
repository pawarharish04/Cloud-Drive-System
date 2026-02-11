package com.cloud.file.client;

import com.cloud.file.client.dto.FileMetadataResponse;
import com.cloud.file.client.dto.MetadataAddChunkRequest;
import com.cloud.file.client.dto.MetadataChunkResponse;
import com.cloud.file.client.dto.MetadataInitiateRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "metadata-service", url = "${metadata-service.url}")
public interface MetadataClient {

    @Deprecated
    @PostMapping("/metadata")
    void saveMetadata(@RequestBody Object metadata);

    @PostMapping("/metadata/initiate")
    Long initiateSession(@RequestBody MetadataInitiateRequest request);

    @PostMapping("/metadata/{fileId}/chunk")
    void addChunk(@PathVariable("fileId") Long fileId, @RequestBody MetadataAddChunkRequest request);

    @GetMapping("/metadata/{fileId}")
    FileMetadataResponse getFile(@PathVariable("fileId") Long fileId);

    @GetMapping("/metadata/{fileId}/chunks")
    List<MetadataChunkResponse> getUploadedChunks(@PathVariable("fileId") Long fileId);

    @PostMapping("/metadata/{fileId}/complete")
    void completeSession(@PathVariable("fileId") Long fileId);

    @PostMapping("/metadata/{fileId}/abort")
    void abortSession(@PathVariable("fileId") Long fileId);
}

package com.cloud.metadata.controller;

import com.cloud.metadata.dto.FileMetadataRequest;
import com.cloud.metadata.dto.FileMetadataResponse;
import com.cloud.metadata.service.MetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/metadata")
@RequiredArgsConstructor
public class MetadataController {

    private final MetadataService metadataService;

    // --- Legacy/Generic ---
    @PostMapping
    public FileMetadataResponse saveMetadata(@RequestBody FileMetadataRequest request) {
        return metadataService.saveMetadata(request);
    }

    // --- Chunked Upload Flow ---
    @PostMapping("/initiate")
    public Long initiateSession(@RequestBody com.cloud.metadata.dto.InitiateSessionRequest request) {
        return metadataService.initiateSession(
                request.getFileName(),
                request.getUserId(),
                request.getUploadId(),
                request.getTotalChunks(),
                request.getSize(),
                request.getContentType());
    }

    @PostMapping("/{fileId}/chunk")
    public void addChunk(@PathVariable Long fileId, @RequestBody com.cloud.metadata.dto.AddChunkRequest request) {
        metadataService.addChunk(fileId, request.getChunkNumber(), request.getEtag(), request.getSize());
    }

    @GetMapping("/{fileId}/chunks")
    public java.util.List<com.cloud.metadata.entity.ChunkMetadata> getChunks(@PathVariable Long fileId) {
        return metadataService.getUploadedChunks(fileId);
    }

    @PostMapping("/{fileId}/complete")
    public void completeSession(@PathVariable Long fileId) {
        metadataService.completeSession(fileId);
    }

    @PostMapping("/{fileId}/abort")
    public void abortSession(@PathVariable Long fileId) {
        metadataService.abortSession(fileId);
    }

    @GetMapping("/{fileId}")
    public FileMetadataResponse getFile(@PathVariable Long fileId) {
        return metadataService.getFileById(fileId);
    }

    @GetMapping("/user/{owner}")
    public List<FileMetadataResponse> getUserFiles(@PathVariable String owner) {
        return metadataService.getFilesByOwner(owner);
    }
}

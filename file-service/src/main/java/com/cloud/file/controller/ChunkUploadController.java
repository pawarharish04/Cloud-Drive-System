package com.cloud.file.controller;

import com.cloud.file.dto.*;
import com.cloud.file.service.ChunkUploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile; // Not used unless we accept multipart request for chunk data

import java.io.IOException;

@RestController
@RequestMapping("/files/upload")
@RequiredArgsConstructor
public class ChunkUploadController {

    private final ChunkUploadService chunkUploadService;

    @PostMapping("/initiate")
    public ResponseEntity<InitiateUploadResponse> initiateUpload(@Valid @RequestBody InitiateUploadRequest request) {
        return ResponseEntity.ok(chunkUploadService.initiateUpload(request));
    }

    @PostMapping("/chunk")
    public ResponseEntity<ChunkUploadResponse> uploadChunk(@Valid @RequestBody ChunkUploadRequest request) {
        return ResponseEntity.ok(chunkUploadService.uploadChunk(request));
    }

    @PostMapping("/complete")
    public ResponseEntity<CompleteUploadResponse> completeUpload(@Valid @RequestBody CompleteUploadRequest request) {
        return ResponseEntity.ok(chunkUploadService.completeUpload(request));
    }
}

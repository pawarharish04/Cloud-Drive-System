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

    @PostMapping
    public FileMetadataResponse saveMetadata(@RequestBody FileMetadataRequest request) {
        return metadataService.saveMetadata(request);
    }

    @GetMapping("/user/{owner}")
    public List<FileMetadataResponse> getUserFiles(@PathVariable String owner) {
        return metadataService.getFilesByOwner(owner);
    }
}

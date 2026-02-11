package com.cloud.file.controller;

import com.cloud.file.service.FileDownloadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@Slf4j
public class FileDownloadController {

    private final FileDownloadService fileDownloadService;

    @GetMapping("/{fileId}/download")
    public ResponseEntity<Map<String, String>> getDownloadUrl(
            @PathVariable String fileId,
            @RequestHeader("X-User-Id") String userId) {

        String url = fileDownloadService.generateDownloadUrl(fileId, userId);

        return ResponseEntity.ok(Collections.singletonMap("downloadUrl", url));
    }
}

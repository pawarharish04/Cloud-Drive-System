package com.cloud.file.service;

import com.cloud.file.client.MetadataClient;
import com.cloud.file.dto.FileUploadResponse;
import com.cloud.file.storage.S3StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class FileService {

    private final S3StorageService storageService;
    private final MetadataClient metadataClient;

    public FileUploadResponse uploadFile(MultipartFile file) {
        try {
            String fileName = storageService.uploadFile(file);
            
            // Construct metadata object (simplified map/object here)
            // metadataClient.saveMetadata(new MetadataDTO(fileName, file.getSize(), file.getContentType()));
            
            return FileUploadResponse.builder()
                    .fileName(fileName)
                    .size(file.getSize())
                    .message("File uploaded successfully")
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file", e);
        }
    }
}

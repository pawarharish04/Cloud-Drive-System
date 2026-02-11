package com.cloud.metadata.service;

import com.cloud.metadata.dto.FileMetadataRequest;
import com.cloud.metadata.dto.FileMetadataResponse;
import com.cloud.metadata.entity.FileMetadata;
import com.cloud.metadata.repository.FileMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetadataService {

    private final FileMetadataRepository repository;

    public FileMetadataResponse saveMetadata(FileMetadataRequest request) {
        FileMetadata metadata = FileMetadata.builder()
                .fileName(request.getFileName())
                .fileType(request.getFileType())
                .size(request.getSize())
                .s3Key(request.getS3Key())
                .owner(request.getOwner())
                .uploadedAt(LocalDateTime.now())
                .build();

        FileMetadata saved = repository.save(metadata);

        return FileMetadataResponse.builder()
                .id(saved.getId())
                .fileName(saved.getFileName())
                .fileType(saved.getFileType())
                .size(saved.getSize())
                .s3Key(saved.getS3Key())
                .owner(saved.getOwner())
                .uploadedAt(saved.getUploadedAt())
                .build();
    }

    public List<FileMetadataResponse> getFilesByOwner(String owner) {
        return repository.findByOwner(owner).stream()
                .map(file -> FileMetadataResponse.builder()
                        .id(file.getId())
                        .fileName(file.getFileName())
                        .fileType(file.getFileType())
                        .size(file.getSize())
                        .s3Key(file.getS3Key())
                        .owner(file.getOwner())
                        .uploadedAt(file.getUploadedAt())
                        .build())
                .collect(Collectors.toList());
    }
}

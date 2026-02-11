package com.cloud.metadata.repository;

import com.cloud.metadata.entity.FileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {
    List<FileMetadata> findByOwner(String owner);
}

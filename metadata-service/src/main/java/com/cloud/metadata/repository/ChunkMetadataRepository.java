package com.cloud.metadata.repository;

import com.cloud.metadata.entity.ChunkMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChunkMetadataRepository extends JpaRepository<ChunkMetadata, Long> {
    List<ChunkMetadata> findByFileMetadataIdOrderByChunkNumberAsc(Long fileId);

    void deleteByFileMetadataId(Long fileId);
}

package com.cloud.metadata.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Entity to store individual chunk metadata.
 * Links to the parent FileMetadata entity.
 */
@Entity
@Table(name = "chunk_metadata", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "file_id", "chunk_number" })
})
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChunkMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private FileMetadata fileMetadata;

    @Column(name = "chunk_number", nullable = false)
    private Integer chunkNumber;

    @Column(name = "etag", nullable = false)
    private String etag; // S3 ETag for verification

    @Column(name = "size", nullable = false)
    private Long size;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "checksum")
    private String checksum; // Optional: for additional validation

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

package com.example.filestorage.file;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, UUID> {
    Page<FileMetadata> findAllByOwnerIdAndDeletedAtIsNull(UUID ownerId, Pageable pageable);
    Optional<FileMetadata> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id, UUID ownerId);
}

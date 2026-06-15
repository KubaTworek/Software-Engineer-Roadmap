package com.example.filestorage.file;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, UUID> {
    Page<FileMetadata> findAllByOwnerIdAndDeletedAtIsNull(UUID ownerId, Pageable pageable);
    Page<FileMetadata> findAllByOwnerIdAndDeletedAtIsNotNull(UUID ownerId, Pageable pageable);
    Optional<FileMetadata> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id, UUID ownerId);
    Optional<FileMetadata> findByIdAndDeletedAtIsNull(UUID id);
    Optional<FileMetadata> findByIdAndOwnerIdAndDeletedAtIsNotNull(UUID id, UUID ownerId);
    Page<FileMetadata> findAllByOwnerIdAndParentFolderIdAndDeletedAtIsNull(UUID ownerId, UUID parentFolderId, Pageable pageable);
    Page<FileMetadata> findAllByOwnerIdAndParentFolderIdIsNullAndDeletedAtIsNull(UUID ownerId, Pageable pageable);
    List<FileMetadata> findAllByOwnerIdAndParentFolderIdAndDeletedAtIsNull(UUID ownerId, UUID parentFolderId);
    List<FileMetadata> findAllByOwnerIdAndParentFolderIdIsNullAndDeletedAtIsNull(UUID ownerId);
    List<FileMetadata> findTop100ByDeletedAtIsNullOrderByCreatedAtDesc();
    boolean existsByOwnerIdAndParentFolderIdAndNameAndDeletedAtIsNull(UUID ownerId, UUID parentFolderId, String name);
    boolean existsByOwnerIdAndParentFolderIdIsNullAndNameAndDeletedAtIsNull(UUID ownerId, String name);
}

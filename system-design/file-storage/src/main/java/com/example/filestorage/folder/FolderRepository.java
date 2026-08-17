package com.example.filestorage.folder;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FolderRepository extends JpaRepository<Folder, UUID> {
    Optional<Folder> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id, UUID ownerId);
    Optional<Folder> findByIdAndDeletedAtIsNull(UUID id);
    Optional<Folder> findByIdAndOwnerIdAndDeletedAtIsNotNull(UUID id, UUID ownerId);
    boolean existsByOwnerIdAndParentFolderIdAndNameAndDeletedAtIsNull(UUID ownerId, UUID parentFolderId, String name);
    boolean existsByOwnerIdAndParentFolderIdIsNullAndNameAndDeletedAtIsNull(UUID ownerId, String name);
    Page<Folder> findAllByOwnerIdAndParentFolderIdAndDeletedAtIsNull(UUID ownerId, UUID parentFolderId, Pageable pageable);
    Page<Folder> findAllByOwnerIdAndParentFolderIdIsNullAndDeletedAtIsNull(UUID ownerId, Pageable pageable);
    List<Folder> findAllByOwnerIdAndParentFolderIdAndDeletedAtIsNull(UUID ownerId, UUID parentFolderId);
    List<Folder> findAllByOwnerIdAndParentFolderIdIsNullAndDeletedAtIsNull(UUID ownerId);
    Page<Folder> findAllByOwnerIdAndDeletedAtIsNotNull(UUID ownerId, Pageable pageable);
}

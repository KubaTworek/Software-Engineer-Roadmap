package com.example.filestorage.version;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FileVersionRepository extends JpaRepository<FileVersion, UUID> {
    Page<FileVersion> findAllByFileIdOrderByVersionNumberDesc(UUID fileId, Pageable pageable);
    Optional<FileVersion> findByIdAndFileId(UUID id, UUID fileId);

    @Query("select coalesce(max(v.versionNumber), 0) from FileVersion v where v.fileId = :fileId")
    int maxVersionNumber(@Param("fileId") UUID fileId);
}

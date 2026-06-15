package com.example.filestorage.upload;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UploadSessionRepository extends JpaRepository<UploadSession, UUID> {
    Optional<UploadSession> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from UploadSession s where s.id = :id and s.userId = :userId")
    Optional<UploadSession> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    List<UploadSession> findTop100ByStatusInAndExpiresAtBefore(List<UploadStatus> statuses, Instant now);
}

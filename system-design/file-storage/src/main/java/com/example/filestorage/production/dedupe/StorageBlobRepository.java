package com.example.filestorage.production.dedupe;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface StorageBlobRepository extends JpaRepository<StorageBlob, UUID> {
    Optional<StorageBlob> findBySha256(String sha256);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select blob from StorageBlob blob where blob.sha256 = :sha256")
    Optional<StorageBlob> findBySha256ForUpdate(@Param("sha256") String sha256);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select blob from StorageBlob blob where blob.id = :id")
    Optional<StorageBlob> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Serializuje rownolegla rejestracje pierwszego bloba o tym samym hashu.
     * Blokada wiersza nie pomaga, gdy wiersz jeszcze nie istnieje; advisory lock
     * domyka wlasnie to okno wyscigu. To swiadoma granica PostgreSQL.
     */
    @Query(value = "select pg_advisory_xact_lock(hashtextextended(:sha256, 0))", nativeQuery = true)
    void lockHash(@Param("sha256") String sha256);
}

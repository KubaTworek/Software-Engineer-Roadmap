package com.example.filestorage.production.dedupe;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface StorageBlobRepository extends JpaRepository<StorageBlob, UUID> {
    Optional<StorageBlob> findBySha256(String sha256);
}

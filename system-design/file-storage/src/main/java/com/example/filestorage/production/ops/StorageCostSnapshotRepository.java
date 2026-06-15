package com.example.filestorage.production.ops;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface StorageCostSnapshotRepository extends JpaRepository<StorageCostSnapshot, UUID> {}

package com.ridesharing.mvp.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {
    Optional<IdempotencyKey> findByKeyAndUserIdAndEndpoint(String key, UUID userId, String endpoint);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from IdempotencyKey i where i.id = :id")
    Optional<IdempotencyKey> findLockedById(@Param("id") UUID id);
}

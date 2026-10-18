package com.ridesharing.mvp.idempotency;

import com.ridesharing.mvp.user.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Trwały claim klucza działa w krótkiej, niezależnej transakcji. Status FAILED
 * nie znika więc razem z rollbackiem operacji biznesowej, a wygasły worker może
 * zostać bezpiecznie przejęty po upływie lease.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyClaimService {
    private final IdempotencyKeyRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID claimNew(String key, AppUser user, String endpoint, String hash) {
        var row = IdempotencyKey.builder()
                .id(UUID.randomUUID())
                .key(key)
                .user(user)
                .endpoint(endpoint)
                .requestHash(hash)
                .status(IdempotencyStatus.PROCESSING)
                .lockedUntil(Instant.now().plusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(24 * 3600))
                .build();
        repository.saveAndFlush(row);
        return row.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID reclaim(UUID id, String expectedHash) {
        var row = repository.findLockedById(id).orElseThrow();
        if (!row.getRequestHash().equals(expectedHash)) {
            throw new IllegalStateException("Cannot reclaim a different request");
        }
        row.setStatus(IdempotencyStatus.PROCESSING);
        row.setLockedUntil(Instant.now().plusSeconds(60));
        row.setResponseBody(null);
        row.setHttpStatus(null);
        return row.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID id, String responseBody, int httpStatus) {
        var row = repository.findLockedById(id).orElseThrow();
        row.setStatus(IdempotencyStatus.COMPLETED);
        row.setResponseBody(responseBody);
        row.setHttpStatus(httpStatus);
        row.setLockedUntil(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID id) {
        repository.findLockedById(id).ifPresent(row -> {
            row.setStatus(IdempotencyStatus.FAILED);
            row.setLockedUntil(null);
        });
    }
}

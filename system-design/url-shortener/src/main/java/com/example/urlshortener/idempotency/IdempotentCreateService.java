package com.example.urlshortener.idempotency;

import com.example.urlshortener.dto.CreateShortUrlRequest;
import com.example.urlshortener.dto.CreateShortUrlResponse;
import com.example.urlshortener.exception.IdempotencyConflictException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Implements request fingerprinting, response replay and concurrent-key conflict handling. */
@Service
public class IdempotentCreateService {
    private final IdempotencyRecordRepository repository;
    private final IdempotentCreateTransaction transaction;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IdempotentCreateService(
            IdempotencyRecordRepository repository,
            IdempotentCreateTransaction transaction,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.repository = repository;
        this.transaction = transaction;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public CreateShortUrlResponse create(String key, CreateShortUrlRequest request) {
        return create("anonymous", key, request);
    }

    public CreateShortUrlResponse create(String actor, String key, CreateShortUrlRequest request) {
        String storageKey = IdempotencyScope.storageKey(actor, "create-url", key);
        String fingerprint = fingerprint(request);
        IdempotencyRecord existing = repository.findById(storageKey).orElse(null);
        if (existing != null && existing.getExpiresAt().isAfter(Instant.now(clock))) {
            return replay(existing, fingerprint);
        }
        if (existing != null) {
            repository.delete(existing);
            repository.flush();
        }
        try {
            return transaction.execute(storageKey, fingerprint, request);
        } catch (DataIntegrityViolationException concurrentInsert) {
            // The winning transaction has committed before its unique violation is visible here.
            return repository.findById(storageKey)
                    .map(record -> replay(record, fingerprint))
                    .orElseThrow(() -> concurrentInsert);
        }
    }

    private CreateShortUrlResponse replay(IdempotencyRecord record, String fingerprint) {
        if (!MessageDigest.isEqual(
                record.getRequestFingerprint().getBytes(StandardCharsets.US_ASCII),
                fingerprint.getBytes(StandardCharsets.US_ASCII))) {
            throw new IdempotencyConflictException("Idempotency-Key was already used for a different request");
        }
        if (record.getResponseJson() == null) {
            throw new IdempotencyConflictException("Request with this Idempotency-Key is still in progress");
        }
        try {
            return objectMapper.readValue(record.getResponseJson(), CreateShortUrlResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored idempotent response is invalid", exception);
        }
    }

    private String fingerprint(CreateShortUrlRequest request) {
        try {
            byte[] canonical = objectMapper.writeValueAsBytes(request);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot fingerprint request", exception);
        }
    }

}

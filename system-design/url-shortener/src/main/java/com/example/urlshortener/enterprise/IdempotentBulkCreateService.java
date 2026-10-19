package com.example.urlshortener.enterprise;

import com.example.urlshortener.exception.IdempotencyConflictException;
import com.example.urlshortener.idempotency.IdempotencyRecord;
import com.example.urlshortener.idempotency.IdempotencyRecordRepository;
import com.example.urlshortener.idempotency.IdempotencyScope;
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

/** Idempotency boundary for a tenant-owned bulk command. */
@Service
public class IdempotentBulkCreateService {
    private final IdempotencyRecordRepository records;
    private final IdempotentBulkCreateTransaction transaction;
    private final ObjectMapper json;
    private final Clock clock;

    public IdempotentBulkCreateService(IdempotencyRecordRepository records,
            IdempotentBulkCreateTransaction transaction, ObjectMapper json, Clock clock) {
        this.records = records;
        this.transaction = transaction;
        this.json = json;
        this.clock = clock;
    }

    public BulkCreateShortUrlResponse create(long ownerId, String rawKey, BulkCreateShortUrlRequest request) {
        String storageKey = IdempotencyScope.storageKey("enterprise:" + ownerId, "bulk-create", rawKey);
        String fingerprint = fingerprint(request);
        IdempotencyRecord existing = records.findById(storageKey).orElse(null);
        if (existing != null && existing.getExpiresAt().isAfter(Instant.now(clock))) {
            return replay(existing, fingerprint);
        }
        if (existing != null) {
            records.delete(existing);
            records.flush();
        }
        try {
            return transaction.execute(storageKey, fingerprint, ownerId, request);
        } catch (DataIntegrityViolationException concurrent) {
            return records.findById(storageKey).map(record -> replay(record, fingerprint))
                    .orElseThrow(() -> concurrent);
        }
    }

    private BulkCreateShortUrlResponse replay(IdempotencyRecord record, String fingerprint) {
        if (!MessageDigest.isEqual(record.getRequestFingerprint().getBytes(StandardCharsets.US_ASCII),
                fingerprint.getBytes(StandardCharsets.US_ASCII))) {
            throw new IdempotencyConflictException("Idempotency-Key was already used for a different bulk command");
        }
        if (record.getResponseJson() == null) {
            throw new IdempotencyConflictException("Bulk command with this Idempotency-Key is still in progress");
        }
        try {
            return json.readValue(record.getResponseJson(), BulkCreateShortUrlResponse.class);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Stored idempotent bulk response is invalid", failure);
        }
    }

    private String fingerprint(BulkCreateShortUrlRequest request) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(request)));
        } catch (JsonProcessingException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Cannot fingerprint bulk request", failure);
        }
    }
}

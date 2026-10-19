package com.example.urlshortener.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Durable command result; its transaction is the same transaction that creates the URL. */
@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {
    @Id
    @Column(name = "idempotency_key", length = 128)
    private String key;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "response_json", columnDefinition = "TEXT")
    private String responseJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(String key, String requestFingerprint, Instant createdAt, Instant expiresAt) {
        this.key = key;
        this.requestFingerprint = requestFingerprint;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getRequestFingerprint() { return requestFingerprint; }
    public String getResponseJson() { return responseJson; }
    public Instant getExpiresAt() { return expiresAt; }

    public void complete(String responseJson) {
        this.responseJson = responseJson;
    }
}

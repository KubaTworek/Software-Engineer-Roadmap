package com.example.paymentsystem.idempotency;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "idempotency_keys")
@IdClass(IdempotencyRecordId.class)
public class IdempotencyRecord {
    @Id
    @Column(name = "idempotency_key")
    private String idempotencyKey;
    @Id
    @Column(name = "scope")
    private String scope;
    @Column(name = "request_hash")
    private String requestHash;
    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;
    @Column(name = "http_status")
    private Integer httpStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IdempotencyStatus status;
    @Column(name = "resource_id")
    private String resourceId;
    @Column(name = "lease_until", nullable = false)
    private Instant leaseUntil;
    @Column(name = "created_at")
    private Instant createdAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(String idempotencyKey, String scope, String requestHash, Instant leaseUntil) {
        this.idempotencyKey = idempotencyKey;
        this.scope = scope;
        this.requestHash = requestHash;
        this.status = IdempotencyStatus.PROCESSING;
        this.leaseUntil = leaseUntil;
        this.createdAt = Instant.now();
    }

    public void complete(String responseBody, int httpStatus, String resourceId) {
        this.responseBody = responseBody;
        this.httpStatus = httpStatus;
        this.resourceId = resourceId;
        this.status = IdempotencyStatus.COMPLETED;
    }

    public void markUnknown(String responseBody, int httpStatus, String resourceId) {
        this.responseBody = responseBody;
        this.httpStatus = httpStatus;
        this.resourceId = resourceId;
        this.status = IdempotencyStatus.UNKNOWN;
    }

    public void retryable() {
        this.status = IdempotencyStatus.FAILED_RETRYABLE;
    }

    public void reacquire(Instant newLeaseUntil) {
        if (status != IdempotencyStatus.FAILED_RETRYABLE) throw new IllegalStateException("Claim is not retryable");
        status = IdempotencyStatus.PROCESSING;
        leaseUntil = newLeaseUntil;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public IdempotencyStatus getStatus() { return status; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public String getResourceId() { return resourceId; }
}

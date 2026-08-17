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
    @Lob
    @Column(name = "response_body")
    private String responseBody;
    @Column(name = "http_status")
    private int httpStatus;
    @Column(name = "created_at")
    private Instant createdAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(String idempotencyKey, String scope, String requestHash, String responseBody, int httpStatus) {
        this.idempotencyKey = idempotencyKey;
        this.scope = scope;
        this.requestHash = requestHash;
        this.responseBody = responseBody;
        this.httpStatus = httpStatus;
        this.createdAt = Instant.now();
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
}

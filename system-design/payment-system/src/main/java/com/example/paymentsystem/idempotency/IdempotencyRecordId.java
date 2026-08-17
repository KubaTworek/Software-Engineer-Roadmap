package com.example.paymentsystem.idempotency;

import java.io.Serializable;
import java.util.Objects;

public class IdempotencyRecordId implements Serializable {
    private String idempotencyKey;
    private String scope;

    public IdempotencyRecordId() {
    }

    public IdempotencyRecordId(String idempotencyKey, String scope) {
        this.idempotencyKey = idempotencyKey;
        this.scope = scope;
    }

    public boolean equals(Object o) {
        return o instanceof IdempotencyRecordId that && Objects.equals(idempotencyKey, that.idempotencyKey) && Objects.equals(scope, that.scope);
    }

    public int hashCode() {
        return Objects.hash(idempotencyKey, scope);
    }
}

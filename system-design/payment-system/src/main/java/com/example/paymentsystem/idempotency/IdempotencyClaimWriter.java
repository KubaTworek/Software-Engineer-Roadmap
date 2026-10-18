package com.example.paymentsystem.idempotency;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Separate transaction makes a unique-key race observable without poisoning the caller transaction. */
@Component
class IdempotencyClaimWriter {
    private final IdempotencyRecordRepository repository;

    IdempotencyClaimWriter(IdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void insert(IdempotencyRecord record) {
        repository.saveAndFlush(record);
    }
}

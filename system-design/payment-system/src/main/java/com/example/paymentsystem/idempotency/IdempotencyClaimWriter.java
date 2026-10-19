package com.example.paymentsystem.idempotency;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Separate transaction makes a unique-key race observable without poisoning the caller transaction. */
@Repository
class IdempotencyClaimWriter {
    private final EntityManager entityManager;

    IdempotencyClaimWriter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void insert(IdempotencyRecord record) {
        // Assigned composite IDs make Spring Data save() choose merge(). A competing
        // request could then update the winner and incorrectly report ACQUIRED.
        // Ownership requires insert-only semantics: persist + flush lets the unique
        // constraint choose one winner. @Repository translates the losing insert
        // into DataIntegrityViolationException, outside this isolated transaction.
        entityManager.persist(record);
        entityManager.flush();
    }
}

package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation;

/**
 * Describes whether an operation can be safely retried.
 */
public enum IdempotencyRequirement {
    NATURALLY_IDEMPOTENT,
    IDEMPOTENCY_KEY_REQUIRED,
    NOT_RETRY_SAFE
}

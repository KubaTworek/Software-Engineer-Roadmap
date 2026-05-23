package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation;

/**
 * Common rate limiting algorithms.
 */
public enum RateLimitAlgorithm {
    FIXED_WINDOW,
    SLIDING_WINDOW,
    TOKEN_BUCKET,
    LEAKY_BUCKET
}

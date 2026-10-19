package com.example.paymentsystem.psp;

import java.time.Duration;

/**
 * Normalized failure contract for PSP adapters. HTTP codes alone are not
 * enough: retry safety depends on whether the command crossed the remote
 * side-effect boundary.
 */
public class ProviderCallException extends RuntimeException {
    private final ProviderOutcomeCertainty certainty;
    private final Duration retryAfter;

    public ProviderCallException(String message, ProviderOutcomeCertainty certainty) {
        this(message, certainty, null);
    }

    public ProviderCallException(String message, ProviderOutcomeCertainty certainty, Duration retryAfter) {
        super(message);
        this.certainty = certainty;
        this.retryAfter = retryAfter;
    }

    public ProviderOutcomeCertainty certainty() { return certainty; }
    public Duration retryAfter() { return retryAfter; }
    public boolean safeToRetry() { return certainty == ProviderOutcomeCertainty.NOT_SENT; }
}

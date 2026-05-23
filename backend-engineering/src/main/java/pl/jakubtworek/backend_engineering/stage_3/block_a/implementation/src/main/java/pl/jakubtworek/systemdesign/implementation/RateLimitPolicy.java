package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation;

import java.time.Duration;

/**
 * Defines a rate limit for a specific identity.
 *
 * When the policy rejects a request, an HTTP API should usually return
 * 429 Too Many Requests and include Retry-After when possible.
 */
public record RateLimitPolicy(
        String name,
        RateLimitIdentity identity,
        RateLimitAlgorithm algorithm,
        int limit,
        Duration window,
        boolean retryAfterEnabled
) {
    public RateLimitPolicy {
        requireText(name, "name");
        if (identity == null) {
            throw new IllegalArgumentException("identity is required");
        }
        if (algorithm == null) {
            throw new IllegalArgumentException("algorithm is required");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (window == null || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be positive");
        }
    }

    /**
     * IP-only limits can create false positives behind NAT or corporate proxies.
     */
    public boolean canCreateNatFalsePositives() {
        return identity == RateLimitIdentity.IP_ADDRESS;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}

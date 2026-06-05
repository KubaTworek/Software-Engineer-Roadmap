package com.example.ratelimiter;

/**
 * Minimal interface for checking whether a request should be allowed.
 */
public interface RateLimiter {
    RateLimitDecision check(String clientKey);
}

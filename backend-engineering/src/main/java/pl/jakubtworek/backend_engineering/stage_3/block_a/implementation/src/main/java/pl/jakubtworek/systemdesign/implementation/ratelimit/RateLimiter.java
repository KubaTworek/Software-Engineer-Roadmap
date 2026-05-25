package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation.ratelimit;

/**
 * Generic rate limiter abstraction.
 *
 * The identity can be an IP address, API key, user id, tenant id,
 * or any other stable identifier.
 */
public interface RateLimiter {

    RateLimitResult allow(String identity);
}
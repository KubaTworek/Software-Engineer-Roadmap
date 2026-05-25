package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign.concepts.ratelimit;

/**
 * Generic rate limiter.
 *
 * Identity may be IP, API key, user id, tenant id, or partner id.
 */
public interface RateLimiter {

    RateLimitDecision allow(String identity);
}
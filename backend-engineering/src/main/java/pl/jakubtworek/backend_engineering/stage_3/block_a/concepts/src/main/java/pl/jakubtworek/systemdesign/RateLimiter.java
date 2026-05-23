package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign;

/**
 * Minimal rate limiter abstraction.
 *
 * A limiter can be applied per IP, per API key, per user,
 * per tenant, or per any other stable identity.
 */
public interface RateLimiter {

    RateLimitDecision allow(String identity);
}

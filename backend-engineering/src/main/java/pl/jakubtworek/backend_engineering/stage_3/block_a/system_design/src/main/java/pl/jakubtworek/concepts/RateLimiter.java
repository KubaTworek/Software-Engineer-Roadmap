package pl.jakubtworek.backend_engineering.stage_3.block_a.system_design.src.main.java.pl.jakubtworek.concepts;

/**
 * Minimal rate limiter abstraction.
 *
 * A limiter can be applied per IP, per API key, per user,
 * per tenant, or per any other stable identity.
 */
public interface RateLimiter {

    RateLimitDecision allow(String identity);
}

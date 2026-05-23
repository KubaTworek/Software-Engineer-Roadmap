package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation;

/**
 * Identity used for rate limiting.
 *
 * IP-based limits are useful at the edge.
 * Business limits usually need API key, user, or tenant identity.
 */
public enum RateLimitIdentity {
    IP_ADDRESS,
    API_KEY,
    USER_ID,
    TENANT_ID,
    PARTNER_ID
}

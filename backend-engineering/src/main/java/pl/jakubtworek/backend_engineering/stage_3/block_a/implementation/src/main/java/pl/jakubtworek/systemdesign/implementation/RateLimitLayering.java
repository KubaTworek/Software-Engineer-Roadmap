package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation;

/**
 * Represents a two-level rate limiting setup.
 *
 * A common pattern is:
 * - edge limit per IP
 * - application or gateway limit per API key, user, or tenant
 */
public record RateLimitLayering(
        RateLimitPolicy edgePolicy,
        RateLimitPolicy businessPolicy
) {
    public RateLimitLayering {
        if (edgePolicy == null) {
            throw new IllegalArgumentException("edgePolicy is required");
        }
        if (businessPolicy == null) {
            throw new IllegalArgumentException("businessPolicy is required");
        }
    }

    /**
     * Returns true when the design uses different identities at the edge and business layer.
     */
    public boolean hasSeparatedEdgeAndBusinessProtection() {
        return edgePolicy.identity() == RateLimitIdentity.IP_ADDRESS
                && businessPolicy.identity() != RateLimitIdentity.IP_ADDRESS;
    }
}

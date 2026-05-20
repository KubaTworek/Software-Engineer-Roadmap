package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Optional blacklist entry for revoked access tokens.
 *
 * JWT access tokens are stateless by default.
 * If immediate revocation is required,
 * a blacklist can be used until token expiration.
 */
@Entity
public class RevokedAccessToken {

    @Id
    private String tokenId;

    private Instant expiresAt;

    protected RevokedAccessToken() {
        // Required by JPA
    }

    public RevokedAccessToken(String tokenId, Instant expiresAt) {
        this.tokenId = tokenId;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
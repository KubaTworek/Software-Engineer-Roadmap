package pl.jakubtworek.backend_systems_lab_stage_1.block_c.authorization;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Refresh token stored server-side.
 *
 * In production, store only a hashed token value,
 * never a raw refresh token.
 */
@Entity
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Token identifier or hashed token value.
     */
    @Column(nullable = false, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked;

    protected RefreshToken() {
        // Required by JPA
    }

    public RefreshToken(String tokenHash, String username, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.username = username;
        this.expiresAt = expiresAt;
        this.revoked = false;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getUsername() {
        return username;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void revoke() {
        this.revoked = true;
    }
}
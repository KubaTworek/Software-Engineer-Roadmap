package pl.jakubtworek.backend_systems_lab_stage_1.block_c.authorization;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * Handles refresh tokens.
 *
 * Demonstrates refresh token rotation:
 * - client sends old refresh token,
 * - server validates it,
 * - server revokes old token,
 * - server issues a new refresh token.
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    /**
     * Creates a new refresh token.
     *
     * The raw token is returned only once to the client.
     * The database stores only a hash.
     */
    @Transactional
    public String createRefreshToken(String username) {

        String rawToken = generateSecureRandomToken();
        String tokenHash = hash(rawToken);

        RefreshToken refreshToken = new RefreshToken(
                tokenHash,
                username,
                Instant.now().plusSeconds(7 * 24 * 60 * 60)
        );

        repository.save(refreshToken);

        return rawToken;
    }

    /**
     * Validates old refresh token and rotates it.
     *
     * If old token was stolen and already used,
     * it should be rejected because it is revoked.
     */
    @Transactional
    public String rotateRefreshToken(String rawToken) {

        String tokenHash = hash(rawToken);

        RefreshToken existingToken = repository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (existingToken.isRevoked()) {
            throw new IllegalStateException("Refresh token already revoked");
        }

        if (existingToken.isExpired()) {
            throw new IllegalStateException("Refresh token expired");
        }

        /**
         * Revoke old token before issuing a new one.
         */
        existingToken.revoke();

        /**
         * Issue a new token for the same user.
         */
        return createRefreshToken(existingToken.getUsername());
    }

    private String generateSecureRandomToken() {

        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String hash(String rawToken) {

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(rawToken.getBytes());

            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash refresh token", exception);
        }
    }
}
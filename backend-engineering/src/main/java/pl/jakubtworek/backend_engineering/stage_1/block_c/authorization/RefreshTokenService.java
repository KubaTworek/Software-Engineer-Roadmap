package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

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
    private final SecureRandom secureRandom;
    private final Clock clock;

    @Autowired
    public RefreshTokenService(RefreshTokenRepository repository) {
        this(repository, Clock.systemUTC(), new SecureRandom());
    }

    RefreshTokenService(
            RefreshTokenRepository repository,
            Clock clock,
            SecureRandom secureRandom
    ) {
        this.repository = repository;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    /**
     * Creates a new refresh token.
     *
     * The raw token is returned only once to the client.
     * The database stores only a hash.
     */
    @Transactional
    public String createRefreshToken(String username) {

        return issueToken(username, UUID.randomUUID().toString());
    }

    /**
     * Validates old refresh token and rotates it.
     *
     * If old token was stolen and already used,
     * it should be rejected because it is revoked.
     */
    @Transactional
    public RotatedRefreshToken rotateRefreshToken(String rawToken) {

        String tokenHash = hash(rawToken);

        RefreshToken existingToken = repository.findByTokenHash(tokenHash)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (existingToken.isRevoked()) {
            repository.findAllByFamilyId(existingToken.getFamilyId())
                    .forEach(token -> token.revoke("REUSE_DETECTED"));
            throw new RefreshTokenReuseException();
        }

        if (existingToken.isExpired(clock.instant())) {
            existingToken.revoke("EXPIRED");
            throw new InvalidRefreshTokenException();
        }

        /**
         * Revoke old token before issuing a new one.
         */
        existingToken.revoke("ROTATED");

        /**
         * Issue a new token for the same user.
         */
        String username = existingToken.getUsername();
        String rawSuccessor = issueToken(username, existingToken.getFamilyId());
        return new RotatedRefreshToken(rawSuccessor, username);
    }

    private String issueToken(String username, String familyId) {
        String rawToken = generateSecureRandomToken();
        RefreshToken refreshToken = new RefreshToken(
                hash(rawToken),
                username,
                familyId,
                clock.instant().plusSeconds(7 * 24 * 60 * 60)
        );
        repository.save(refreshToken);
        return rawToken;
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

            byte[] hash = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash refresh token", exception);
        }
    }
}

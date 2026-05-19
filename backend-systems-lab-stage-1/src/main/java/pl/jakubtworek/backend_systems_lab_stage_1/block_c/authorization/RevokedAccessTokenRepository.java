package pl.jakubtworek.backend_systems_lab_stage_1.block_c.authorization;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for revoked access tokens.
 */
public interface RevokedAccessTokenRepository
        extends JpaRepository<RevokedAccessToken, String> {
}
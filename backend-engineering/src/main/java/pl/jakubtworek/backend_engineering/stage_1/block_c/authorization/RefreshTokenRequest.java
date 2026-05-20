package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

/**
 * Refresh request containing refresh token.
 */
public record RefreshTokenRequest(
        String refreshToken
) {
}
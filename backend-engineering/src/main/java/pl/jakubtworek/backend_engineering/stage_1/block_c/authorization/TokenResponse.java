package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

import java.util.List;

/**
 * Response returned after successful login.
 *
 * Access token is short-lived.
 * Refresh token is longer-lived and should be rotated.
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        List<String> roles,
        List<String> permissions
) {
}
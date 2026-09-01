package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Refresh request containing refresh token.
 */
public record RefreshTokenRequest(
        @NotBlank
        @Size(max = 2048)
        String refreshToken
) {
}

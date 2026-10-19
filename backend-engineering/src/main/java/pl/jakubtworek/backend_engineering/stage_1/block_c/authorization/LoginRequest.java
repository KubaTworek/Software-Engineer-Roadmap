package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login request.
 */
public record LoginRequest(
        @NotBlank
        @Size(max = 100)
        String username,

        @NotBlank
        @Size(max = 200)
        String password
) {
}

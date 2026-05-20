package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

/**
 * Login request.
 */
public record LoginRequest(
        String username,
        String password
) {
}
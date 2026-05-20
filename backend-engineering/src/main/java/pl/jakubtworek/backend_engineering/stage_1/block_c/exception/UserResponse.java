package pl.jakubtworek.backend_engineering.stage_1.block_c.exception;

/**
 * DTO returned to API client.
 *
 * Notice that password is not exposed in response.
 */
public record UserResponse(
        Long id,
        String username,
        String email
) {
}
package pl.jakubtworek.backend_engineering.stage_1.block_c.test;

/**
 * DTO returned by REST controller.
 */
public record UserResponse(
        Long id,
        String name
) {
}
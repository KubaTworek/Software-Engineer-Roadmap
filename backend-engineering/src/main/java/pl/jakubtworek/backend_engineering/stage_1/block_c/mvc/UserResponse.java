package pl.jakubtworek.backend_engineering.stage_1.block_c.mvc;

/**
 * Response DTO returned from REST controller.
 *
 * Spring MVC will serialize this object to JSON
 * using HttpMessageConverter.
 */
public record UserResponse(
        Long id,
        String username,
        String email
) {
}
package pl.jakubtworek.backend_systems_lab_stage_1.block_c.mvc;

/**
 * Custom argument type injected into controller methods.
 *
 * Controller can declare:
 *
 * public String endpoint(AuthUser user)
 *
 * and Spring MVC will resolve it using custom HandlerMethodArgumentResolver.
 */
public record AuthUser(
        String username
) {
}
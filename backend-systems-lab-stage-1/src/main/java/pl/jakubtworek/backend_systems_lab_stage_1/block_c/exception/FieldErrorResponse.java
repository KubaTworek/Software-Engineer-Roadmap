package pl.jakubtworek.backend_systems_lab_stage_1.block_c.exception;

/**
 * Represents validation error for a single DTO field.
 *
 * Example:
 * {
 *   "field": "email",
 *   "message": "must be a valid email"
 * }
 */
public record FieldErrorResponse(
        String field,
        String message
) {
}
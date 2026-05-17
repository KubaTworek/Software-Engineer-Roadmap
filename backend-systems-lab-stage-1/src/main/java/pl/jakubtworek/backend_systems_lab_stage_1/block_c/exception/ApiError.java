package pl.jakubtworek.backend_systems_lab_stage_1.block_c.exception;

import java.time.Instant;
import java.util.List;

/**
 * Standard error response returned by REST API.
 *
 * The goal is to keep error responses consistent
 * across the whole application.
 *
 * This object should not expose internal details like:
 * - stack traces,
 * - SQL errors,
 * - implementation class names,
 * - sensitive data.
 */
public record ApiError(
        String code,
        String message,
        int status,
        Instant timestamp,
        List<FieldErrorResponse> fieldErrors
) {
    public static ApiError of(
            String code,
            String message,
            int status
    ) {
        return new ApiError(
                code,
                message,
                status,
                Instant.now(),
                List.of()
        );
    }

    public static ApiError withFieldErrors(
            String code,
            String message,
            int status,
            List<FieldErrorResponse> fieldErrors
    ) {
        return new ApiError(
                code,
                message,
                status,
                Instant.now(),
                fieldErrors
        );
    }
}
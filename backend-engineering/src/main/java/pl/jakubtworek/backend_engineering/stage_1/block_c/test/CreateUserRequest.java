package pl.jakubtworek.backend_engineering.stage_1.block_c.test;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO used in controller validation tests.
 */
public record CreateUserRequest(

        @NotBlank(message = "Name is required")
        String name
) {
}
package pl.jakubtworek.backend_systems_lab_stage_1.block_c.exception;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO used for creating a user.
 *
 * Bean Validation annotations define validation rules.
 *
 * Validation is triggered in controller by:
 * @Valid @RequestBody CreateUserRequest request
 */
public record CreateUserRequest(

        /**
         * Username is required and cannot be blank.
         */
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must have 3-50 characters")
        String username,

        /**
         * Email must not be blank and must have valid format.
         */
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,

        /**
         * Password must have a minimum length.
         *
         * In real applications, password rules can be more complex.
         */
        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must have at least 8 characters")
        String password
) {
}
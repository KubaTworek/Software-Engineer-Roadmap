package pl.jakubtworek.backend_engineering.stage_1.block_c.mvc;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO used by @RequestBody.
 *
 * Spring MVC will deserialize JSON request body
 * into this object using HttpMessageConverter.
 *
 * For JSON, Spring usually uses MappingJackson2HttpMessageConverter.
 */
public record CreateUserRequest(

        /**
         * Bean Validation annotations are checked
         * when controller parameter is annotated with @Valid.
         */
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must have 3-50 characters")
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email
) {
}
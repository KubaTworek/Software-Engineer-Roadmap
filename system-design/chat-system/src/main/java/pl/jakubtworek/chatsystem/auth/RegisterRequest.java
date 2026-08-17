package pl.jakubtworek.chatsystem.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 40) String username,
        @NotBlank @Email @Size(max = 120) String email,
        @NotBlank @Size(min = 8, max = 120) String password,
        @NotBlank @Size(min = 2, max = 80) String displayName
) {}

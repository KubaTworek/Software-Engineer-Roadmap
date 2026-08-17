package com.example.ecommerce.auth.dto;

import com.example.ecommerce.auth.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 6) String password,
            @NotBlank String fullName
    ) {}

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {}

    public record AuthResponse(
            String token,
            UserResponse user
    ) {}

    public record UserResponse(
            Long id,
            String email,
            String fullName,
            Set<UserRole> roles
    ) {}
}

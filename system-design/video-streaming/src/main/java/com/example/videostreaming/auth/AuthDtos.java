package com.example.videostreaming.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(@Email @NotBlank String email, @Size(min = 8, max = 128) String password) {}
    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
    public record AuthResponse(String accessToken, String tokenType, UUID userId, String email, UserRole role) {}
    public record MeResponse(UUID userId, String email, UserRole role) {}
}

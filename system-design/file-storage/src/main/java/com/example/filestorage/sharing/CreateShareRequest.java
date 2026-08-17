package com.example.filestorage.sharing;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record CreateShareRequest(
        @Email @NotBlank String email,
        @NotNull PermissionRole role,
        Instant expiresAt
) {}

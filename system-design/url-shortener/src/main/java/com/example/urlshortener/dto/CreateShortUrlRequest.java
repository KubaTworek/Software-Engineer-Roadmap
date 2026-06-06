package com.example.urlshortener.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateShortUrlRequest(
    @NotBlank
    @Size(max = 4096)
    String longUrl,

    @Size(min = 3, max = 32)
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]{2,31}$", message = "must contain 3-32 lowercase letters, digits, underscores or hyphens and start with a letter or digit")
    String customAlias,

    @Future
    Instant expiresAt
) {
}

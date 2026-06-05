package com.example.urlshortener.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateShortUrlRequest(
    @NotBlank @Size(max = 4096) String longUrl,
    @Future Instant expiresAt
) {}

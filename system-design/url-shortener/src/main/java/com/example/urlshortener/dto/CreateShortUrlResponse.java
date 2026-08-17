package com.example.urlshortener.dto;

import java.time.Instant;

public record CreateShortUrlResponse(
    Long id,
    String shortCode,
    String shortUrl,
    String longUrl,
    Instant expiresAt,
    Instant createdAt
) {}

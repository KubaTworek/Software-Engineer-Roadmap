package com.example.urlshortener.dto;

import com.example.urlshortener.model.UrlStatus;
import java.time.Instant;

public record UrlDetailsResponse(
    Long id,
    String shortCode,
    String shortUrl,
    String longUrl,
    UrlStatus status,
    Instant expiresAt,
    Instant createdAt,
    Instant updatedAt
) {}

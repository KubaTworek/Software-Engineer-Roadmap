package com.example.urlshortener.enterprise;

import java.time.Instant;

public record CreateEnterpriseApiKeyResponse(
    Long id,
    String name,
    String apiKey,
    String tier,
    int rateLimitPerMinute,
    Instant expiresAt,
    Instant createdAt
) {}

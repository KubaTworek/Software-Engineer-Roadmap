package com.example.urlshortener.edge;

import com.example.urlshortener.model.UrlStatus;
import java.time.Instant;

public record EdgeLookupResponse(
    String shortCode,
    String longUrl,
    UrlStatus status,
    Instant expiresAt,
    String regionId,
    long cacheTtlSeconds,
    boolean redirectable
) {}

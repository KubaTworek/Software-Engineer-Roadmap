package com.example.urlshortener.storage;

import com.example.urlshortener.model.UrlStatus;
import java.time.Instant;

public record UrlLookupRecord(
    String shortCode,
    String longUrl,
    UrlStatus status,
    Instant expiresAt,
    String regionId,
    Instant updatedAt,
    long version
) {
    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }
}

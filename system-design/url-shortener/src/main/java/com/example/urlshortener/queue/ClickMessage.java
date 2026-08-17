package com.example.urlshortener.queue;

import java.time.Instant;

public record ClickMessage(
    String eventId,
    String shortCode,
    Instant clickedAt,
    String ipAddress,
    String userAgent,
    String referrer,
    String country,
    String requestId
) {
}

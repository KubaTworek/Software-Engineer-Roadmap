package com.example.autocomplete.pipeline;

import java.time.Instant;

public record QueryEvent(String userId, String sessionId, String query, String locale, String country, String clientIp,
                         Instant timestamp) {
}

package com.example.autocomplete.feedback;

import java.time.Instant;
import java.util.List;

public record FeedbackEvent(
        String eventId,
        FeedbackType type,
        String protectedUserId,
        String protectedSessionId,
        String sanitizedQuery,
        List<String> suggestionIds,
        String protectedIp,
        Instant occurredAt,
        int schemaVersion
) {}

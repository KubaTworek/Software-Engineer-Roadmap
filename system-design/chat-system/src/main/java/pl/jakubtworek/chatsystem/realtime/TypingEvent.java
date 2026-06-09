package pl.jakubtworek.chatsystem.realtime;

import java.time.Instant;
import java.util.UUID;

public record TypingEvent(
        UUID conversationId,
        UUID userId,
        boolean typing,
        Instant occurredAt
) {}

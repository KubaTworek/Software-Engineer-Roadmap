package pl.jakubtworek.chatsystem.outbox;

import java.time.Instant;
import java.util.UUID;

public record MessageCreatedEvent(
        UUID messageId,
        UUID conversationId,
        UUID senderId,
        Instant createdAt
) {}

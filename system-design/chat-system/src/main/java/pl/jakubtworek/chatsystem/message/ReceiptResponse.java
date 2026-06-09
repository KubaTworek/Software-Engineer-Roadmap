package pl.jakubtworek.chatsystem.message;

import java.time.Instant;
import java.util.UUID;

public record ReceiptResponse(
        UUID conversationId,
        UUID messageId,
        UUID userId,
        MessageStatus status,
        Instant deliveredAt,
        Instant readAt
) {}

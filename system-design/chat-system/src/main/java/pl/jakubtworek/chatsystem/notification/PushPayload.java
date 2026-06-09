package pl.jakubtworek.chatsystem.notification;

import java.util.UUID;

public record PushPayload(
        UUID recipientId,
        UUID conversationId,
        UUID messageId,
        String title,
        String body
) {}

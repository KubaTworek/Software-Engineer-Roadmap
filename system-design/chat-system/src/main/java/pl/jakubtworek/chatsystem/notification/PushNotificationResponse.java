package pl.jakubtworek.chatsystem.notification;

import java.time.Instant;
import java.util.UUID;

public record PushNotificationResponse(
        UUID id,
        UUID recipientId,
        UUID conversationId,
        UUID messageId,
        String title,
        String body,
        PushNotificationStatus status,
        Instant createdAt,
        Instant sentAt
) {
    public static PushNotificationResponse from(PushNotification notification) {
        return new PushNotificationResponse(
                notification.getId(),
                notification.getRecipient().getId(),
                notification.getConversation().getId(),
                notification.getMessage().getId(),
                notification.getTitle(),
                notification.getBody(),
                notification.getStatus(),
                notification.getCreatedAt(),
                notification.getSentAt()
        );
    }
}

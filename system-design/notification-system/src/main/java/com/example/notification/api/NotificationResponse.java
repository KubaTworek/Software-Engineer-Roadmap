package com.example.notification.api;

import com.example.notification.domain.Notification;
import com.example.notification.domain.NotificationChannel;
import com.example.notification.domain.NotificationStatus;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String recipient,
        String subject,
        String message,
        NotificationChannel channel,
        NotificationStatus status,
        String failureReason,
        Instant createdAt,
        Instant sentAt
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getRecipient(),
                notification.getSubject(),
                notification.getMessage(),
                notification.getChannel(),
                notification.getStatus(),
                notification.getFailureReason(),
                notification.getCreatedAt(),
                notification.getSentAt()
        );
    }
}

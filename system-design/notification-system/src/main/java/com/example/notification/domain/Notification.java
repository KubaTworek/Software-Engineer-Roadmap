package com.example.notification.domain;

import java.time.Instant;
import java.util.UUID;

public class Notification {

    private final UUID id;
    private final String recipient;
    private final String subject;
    private final String message;
    private final NotificationChannel channel;
    private NotificationStatus status;
    private String failureReason;
    private final Instant createdAt;
    private Instant sentAt;

    private Notification(UUID id,
                         String recipient,
                         String subject,
                         String message,
                         NotificationChannel channel,
                         NotificationStatus status,
                         String failureReason,
                         Instant createdAt,
                         Instant sentAt) {
        this.id = id;
        this.recipient = recipient;
        this.subject = subject;
        this.message = message;
        this.channel = channel;
        this.status = status;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.sentAt = sentAt;
    }

    public static Notification createEmail(String recipient, String subject, String message) {
        return new Notification(
                UUID.randomUUID(),
                recipient,
                subject,
                message,
                NotificationChannel.EMAIL,
                NotificationStatus.CREATED,
                null,
                Instant.now(),
                null
        );
    }

    public void markAsSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = Instant.now();
        this.failureReason = null;
    }

    public void markAsFailed(String reason) {
        this.status = NotificationStatus.FAILED;
        this.failureReason = reason;
    }

    public UUID getId() {
        return id;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getSubject() {
        return subject;
    }

    public String getMessage() {
        return message;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}

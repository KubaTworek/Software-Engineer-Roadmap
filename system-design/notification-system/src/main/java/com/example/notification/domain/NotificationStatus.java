package com.example.notification.domain;

public enum NotificationStatus {
    CREATED,
    QUEUED,
    PROCESSING,
    SENT,
    UNKNOWN,
    PARTIALLY_DELIVERED,
    DELIVERED,
    BOUNCED,
    FAILED,
    EXPIRED,
    CANCELLED,
    ARCHIVED
}

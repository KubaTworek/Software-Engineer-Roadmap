package com.example.notification.application;

import com.example.notification.domain.Notification;

public record CreateNotificationResult(Notification notification, boolean duplicate, String duplicateReason) {
}

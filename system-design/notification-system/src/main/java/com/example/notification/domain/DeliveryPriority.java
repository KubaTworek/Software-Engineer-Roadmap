package com.example.notification.domain;

/** Priorytet steruje schedulingiem, a nie samą liczbą retry. */
public enum DeliveryPriority {
    CRITICAL, HIGH, NORMAL, LOW;

    public static DeliveryPriority forType(NotificationType type) {
        return switch (type) {
            case PASSWORD_RESET, SECURITY_ALERT -> CRITICAL;
            case PAYMENT_FAILED -> HIGH;
            case WEEKLY_DIGEST -> NORMAL;
            case MARKETING_PROMOTION, CAMPAIGN_MESSAGE -> LOW;
        };
    }
}

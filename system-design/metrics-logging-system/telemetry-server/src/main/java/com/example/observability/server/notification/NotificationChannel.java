package com.example.observability.server.notification;

import com.example.observability.server.alert.AlertEvent;
import com.example.observability.server.alert.AlertRule;

public interface NotificationChannel {
    String type();

    void notify(AlertRule rule, AlertEvent event, String target);
}

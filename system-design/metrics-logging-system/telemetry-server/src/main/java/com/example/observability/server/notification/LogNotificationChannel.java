package com.example.observability.server.notification;

import com.example.observability.server.alert.AlertEvent;
import com.example.observability.server.alert.AlertRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LogNotificationChannel implements NotificationChannel {
    private static final Logger log = LoggerFactory.getLogger(LogNotificationChannel.class);

    @Override
    public String type() {
        return "log";
    }

    @Override
    public void notify(AlertRule rule, AlertEvent event, String target) {
        log.warn("ROUTED ALERT target={} tenant={} rule={} status={} observed={} threshold={} message={}",
                target, event.tenantId(), rule.getName(), event.status(), event.observedValue(), event.threshold(), event.message());
    }
}

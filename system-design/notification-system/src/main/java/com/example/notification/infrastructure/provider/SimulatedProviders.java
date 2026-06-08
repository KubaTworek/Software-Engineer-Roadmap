package com.example.notification.infrastructure.provider;

import com.example.notification.application.Exceptions;
import com.example.notification.application.Ports;
import com.example.notification.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

public final class SimulatedProviders {
    private SimulatedProviders() {}

    static abstract class BaseProvider implements Ports.NotificationProvider {
        private final Logger log = LoggerFactory.getLogger(getClass());

        @Override
        public ProviderSendResult send(String tenantId, String recipient, RenderedNotification notification) {
            if (recipient != null && recipient.contains("permanent-failure")) {
                throw new Exceptions.PermanentProviderException("Simulated permanent provider failure for " + providerName());
            }
            if (recipient != null && recipient.contains("temporary-failure")) {
                throw new Exceptions.TransientProviderException("Simulated temporary provider failure for " + providerName());
            }
            String id = providerName() + "-" + UUID.randomUUID();
            log.info("[tenant={}, channel={}, provider={}] recipient={}, providerMessageId={}, subject={}, body={}",
                    tenantId, channel(), providerName(), recipient, id, notification.subject(), notification.body());
            return new ProviderSendResult(providerName(), id);
        }
    }

    @Component @Order(1)
    public static class EmailPrimary extends BaseProvider {
        public Channel channel() { return Channel.EMAIL; }
        public String providerName() { return "sendgrid-primary"; }
    }

    @Component @Order(2)
    public static class EmailFallback extends BaseProvider {
        public Channel channel() { return Channel.EMAIL; }
        public String providerName() { return "ses-fallback"; }
    }

    @Component @Order(1)
    public static class SmsPrimary extends BaseProvider {
        public Channel channel() { return Channel.SMS; }
        public String providerName() { return "twilio-primary"; }
    }

    @Component @Order(2)
    public static class SmsFallback extends BaseProvider {
        public Channel channel() { return Channel.SMS; }
        public String providerName() { return "vonage-fallback"; }
    }

    @Component
    public static class Push extends BaseProvider {
        public Channel channel() { return Channel.PUSH; }
        public String providerName() { return "fcm-primary"; }
    }

    @Component
    public static class InApp extends BaseProvider {
        public Channel channel() { return Channel.IN_APP; }
        public String providerName() { return "in-app-store"; }
    }
}

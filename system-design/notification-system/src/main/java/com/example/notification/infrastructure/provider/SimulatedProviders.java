package com.example.notification.infrastructure.provider;

import com.example.notification.application.Exceptions;
import com.example.notification.application.Ports;
import com.example.notification.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.util.UUID;
import java.nio.charset.StandardCharsets;

@Profile("!http-provider")
public final class SimulatedProviders {
    private SimulatedProviders() {}

    static abstract class BaseProvider implements Ports.NotificationProvider {
        private final Logger log = LoggerFactory.getLogger(getClass());

        @Override
        public ProviderSendResult send(String tenantId, String recipient, RenderedNotification notification,
                                       String idempotencyKey) {
            if (recipient != null && recipient.contains("permanent-failure")) {
                throw new Exceptions.PermanentProviderException("Simulated permanent provider failure for " + providerName());
            }
            if (recipient != null && recipient.contains("temporary-failure")) {
                throw new Exceptions.ProviderCallException("Simulated connect failure for " + providerName(),
                        Exceptions.OutcomeCertainty.NOT_SENT);
            }
            if (recipient != null && recipient.contains("unknown-outcome")) {
                throw new Exceptions.ProviderCallException("Provider timed out after request dispatch",
                        Exceptions.OutcomeCertainty.UNKNOWN);
            }
            String id = providerName() + "-" + UUID.nameUUIDFromBytes(
                    (providerName() + ":" + idempotencyKey).getBytes(StandardCharsets.UTF_8));
            log.info("[tenant={}, channel={}, provider={}] recipient={}, providerMessageId={}",
                    tenantId, channel(), providerName(), mask(recipient), id);
            return new ProviderSendResult(providerName(), id);
        }

        private String mask(String recipient) {
            if (recipient == null || recipient.length() < 4) return "***";
            return "***" + recipient.substring(recipient.length() - 3);
        }
    }

    @Component @Profile("!http-provider") @Order(1)
    public static class EmailPrimary extends BaseProvider {
        public Channel channel() { return Channel.EMAIL; }
        public String providerName() { return "sendgrid-primary"; }
    }

    @Component @Profile("!http-provider") @Order(2)
    public static class EmailFallback extends BaseProvider {
        public Channel channel() { return Channel.EMAIL; }
        public String providerName() { return "ses-fallback"; }
    }

    @Component @Profile("!http-provider") @Order(1)
    public static class SmsPrimary extends BaseProvider {
        public Channel channel() { return Channel.SMS; }
        public String providerName() { return "twilio-primary"; }
    }

    @Component @Profile("!http-provider") @Order(2)
    public static class SmsFallback extends BaseProvider {
        public Channel channel() { return Channel.SMS; }
        public String providerName() { return "vonage-fallback"; }
    }

    @Component @Profile("!http-provider")
    public static class Push extends BaseProvider {
        public Channel channel() { return Channel.PUSH; }
        public String providerName() { return "fcm-primary"; }
    }

    @Component @Profile("!http-provider")
    public static class InApp extends BaseProvider {
        public Channel channel() { return Channel.IN_APP; }
        public String providerName() { return "in-app-store"; }
    }
}

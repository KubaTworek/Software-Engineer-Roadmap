package com.example.notification.infrastructure.provider;

import com.example.notification.application.Ports;
import com.example.notification.domain.DeliveryAttempt;
import com.example.notification.domain.NotificationJob;
import org.springframework.stereotype.Component;

/** Deterministyczna symulacja API statusowego providera używana w laboratorium. */
@Component
public class SimulatedProviderStatusGateway implements Ports.ProviderStatusGateway {
    @Override public Ports.ProviderLookupResult lookup(NotificationJob job, DeliveryAttempt attempt) {
        String recipient = job.getContactPoint().valueFor(job.getChannel());
        if (recipient != null && recipient.contains("reconcile-delivered")) return Ports.ProviderLookupResult.DELIVERED;
        if (recipient != null && recipient.contains("reconcile-accepted")) return Ports.ProviderLookupResult.ACCEPTED;
        if (recipient != null && recipient.contains("reconcile-not-sent")) return Ports.ProviderLookupResult.NOT_SENT;
        return Ports.ProviderLookupResult.STILL_UNKNOWN;
    }
}

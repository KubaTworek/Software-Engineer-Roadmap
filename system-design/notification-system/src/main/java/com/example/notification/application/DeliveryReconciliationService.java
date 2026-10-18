package com.example.notification.application;

import com.example.notification.domain.DeliveryAttempt;
import com.example.notification.domain.NotificationJob;
import com.example.notification.domain.ProviderSendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Clock;

/** Naprawia niejednoznaczne wyniki na podstawie źródła prawdy providera. */
@Service
public class DeliveryReconciliationService {
    private final Ports.NotificationJobRepository jobs;
    private final Ports.DeliveryAttemptRepository attempts;
    private final Ports.ProviderStatusGateway providerStatus;
    private final Ports.NotificationQueue queue;
    private final Ports.NotificationRepository notifications;
    private final NotificationStatusPolicy statusPolicy;
    private final Clock clock;

    public DeliveryReconciliationService(Ports.NotificationJobRepository jobs,
                                         Ports.DeliveryAttemptRepository attempts,
                                         Ports.ProviderStatusGateway providerStatus,
                                         Ports.NotificationQueue queue,
                                         Ports.NotificationRepository notifications,
                                         NotificationStatusPolicy statusPolicy,
                                         Clock clock) {
        this.jobs = jobs; this.attempts = attempts; this.providerStatus = providerStatus; this.queue = queue;
        this.notifications = notifications; this.statusPolicy = statusPolicy;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${notification.reconciliation.fixed-delay-ms:5000}")
    public void reconcileUnconfirmedDeliveries() {
        for (NotificationJob job : jobs.findReconcilableOlderThan(clock.instant().minusSeconds(5))) reconcile(job);
    }

    @Transactional
    public Ports.ProviderLookupResult reconcile(NotificationJob job) {
        DeliveryAttempt attempt = attempts.findByJobId(job.getId()).stream().reduce((a, b) -> b)
                .orElseThrow(() -> new IllegalStateException("UNKNOWN job has no delivery attempt"));
        Ports.ProviderLookupResult result = providerStatus.lookup(job, attempt);
        if (result == Ports.ProviderLookupResult.DELIVERED) {
            if (job.getStatus() == com.example.notification.domain.NotificationStatus.UNKNOWN) {
                job.resolveUnknownAsSent(new ProviderSendResult(attempt.providerName(),
                        "reconciled-" + job.getId()));
            }
            job.markDelivered();
            jobs.save(job);
            refreshParent(job);
        } else if (result == Ports.ProviderLookupResult.ACCEPTED
                && job.getStatus() == com.example.notification.domain.NotificationStatus.UNKNOWN) {
            job.resolveUnknownAsSent(new ProviderSendResult(attempt.providerName(),
                    "reconciled-" + job.getId()));
            jobs.save(job);
            refreshParent(job);
        } else if (result == Ports.ProviderLookupResult.NOT_SENT
                && job.getStatus() == com.example.notification.domain.NotificationStatus.UNKNOWN) {
            job.resolveUnknownForRetry(clock.instant());
            jobs.save(job);
            queue.enqueue(job.getId());
            refreshParent(job);
        }
        return result;
    }

    private void refreshParent(NotificationJob job) {
        notifications.findById(job.getTenantId(), job.getNotificationId()).ifPresent(notification -> {
            statusPolicy.apply(notification, jobs.findByNotificationId(job.getTenantId(), job.getNotificationId()));
            notifications.save(notification);
        });
    }
}

package com.example.notification.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Koordynuje usunięcie PII, nie usuwając dowodu, że operacja techniczna istniała. */
@Service
public class PersonalDataLifecycleService {
    public record ErasureResult(int notifications, int jobs) { }
    private final Ports.NotificationRepository notifications;
    private final Ports.NotificationJobRepository jobs;
    private final Ports.PreferenceService preferences;
    private final Ports.AuditRepository audit;
    private final SuppressionService suppressions;

    public PersonalDataLifecycleService(Ports.NotificationRepository notifications,
                                        Ports.NotificationJobRepository jobs,
                                        Ports.PreferenceService preferences,
                                        Ports.AuditRepository audit,
                                        SuppressionService suppressions) {
        this.notifications = notifications; this.jobs = jobs; this.preferences = preferences;
        this.audit = audit; this.suppressions = suppressions;
    }
    @Transactional
    public ErasureResult erase(String tenantId, String userId) {
        notifications.findAll(tenantId).stream().filter(notification -> notification.getUserId().equals(userId))
                .forEach(notification -> notification.getSelectedChannels().forEach(channel -> suppressions.forget(
                        tenantId, channel, notification.getContactPoint().valueFor(channel))));
        int notificationCount = notifications.anonymizeUser(tenantId, userId);
        int jobCount = jobs.anonymizeUser(tenantId, userId);
        preferences.deleteUser(tenantId, userId);
        audit.anonymizeUser(tenantId, userId);
        return new ErasureResult(notificationCount, jobCount);
    }
}

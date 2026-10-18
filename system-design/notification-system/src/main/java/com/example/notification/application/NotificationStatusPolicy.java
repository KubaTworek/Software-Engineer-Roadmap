package com.example.notification.application;

import com.example.notification.domain.Notification;
import com.example.notification.domain.NotificationJob;
import com.example.notification.domain.NotificationStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/** Jedno miejsce definiujące semantykę agregacji wyników wielu kanałów. */
@Component
public class NotificationStatusPolicy {
    public NotificationStatus derive(List<NotificationJob> jobs) {
        if (jobs.isEmpty()) return NotificationStatus.CREATED;
        if (jobs.stream().anyMatch(job -> job.getStatus() == NotificationStatus.UNKNOWN)) return NotificationStatus.UNKNOWN;
        if (jobs.stream().allMatch(job -> job.getStatus() == NotificationStatus.DELIVERED)) return NotificationStatus.DELIVERED;
        if (jobs.stream().allMatch(NotificationJob::isTerminal)
                && jobs.stream().anyMatch(job -> job.getStatus() == NotificationStatus.DELIVERED)) {
            return NotificationStatus.PARTIALLY_DELIVERED;
        }
        if (jobs.stream().allMatch(NotificationJob::isTerminal)) return NotificationStatus.FAILED;
        if (jobs.stream().allMatch(job -> job.getStatus() == NotificationStatus.SENT
                || job.getStatus() == NotificationStatus.DELIVERED)) return NotificationStatus.SENT;
        if (jobs.stream().anyMatch(job -> job.getStatus() == NotificationStatus.PROCESSING)) return NotificationStatus.PROCESSING;
        return NotificationStatus.QUEUED;
    }

    public void apply(Notification notification, List<NotificationJob> jobs) {
        if (notification.isExpired()) { notification.markExpired(); return; }
        NotificationStatus target = derive(jobs);
        if (notification.getStatus() == target || notification.isTerminal()) return;
        switch (target) {
            case QUEUED -> { if (notification.getStatus() == NotificationStatus.CREATED) notification.markQueued(); }
            case PROCESSING -> notification.markProcessing();
            case SENT -> notification.markSent();
            case UNKNOWN -> notification.markUnknown();
            case DELIVERED -> notification.markDelivered();
            case PARTIALLY_DELIVERED -> notification.markPartiallyDelivered();
            case FAILED -> notification.markFailed();
            default -> { }
        }
    }
}

package com.example.notification.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedNotificationEventRepository extends JpaRepository<ProcessedNotificationEvent, Long> {
}

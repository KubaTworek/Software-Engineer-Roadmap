package com.example.notification.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    @Query("select e from OutboxEvent e " +
            "where e.eventType in ('OrderCreated', 'PaymentSucceeded', 'PaymentFailed', 'OrderCancelled') " +
            "and e.id not in (select p.outboxEventId from ProcessedNotificationEvent p) " +
            "order by e.id asc")
    List<OutboxEvent> findUnprocessedNotificationEvents();
}

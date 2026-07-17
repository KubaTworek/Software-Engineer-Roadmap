package com.example.notification;

import com.example.notification.outbox.OutboxEventRepository;
import com.example.notification.outbox.ProcessedNotificationEvent;
import com.example.notification.outbox.ProcessedNotificationEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NotificationWorker {
    private final OutboxEventRepository outbox;
    private final ProcessedNotificationEventRepository processed;
    private final EmailClient emailClient;
    private final int batchSize;
    private final Counter sent;

    public NotificationWorker(
            OutboxEventRepository outbox,
            ProcessedNotificationEventRepository processed,
            EmailClient emailClient,
            @Value("${app.worker.batch-size:100}") int batchSize,
            MeterRegistry registry
    ) {
        this.outbox = outbox;
        this.processed = processed;
        this.emailClient = emailClient;
        this.batchSize = batchSize;
        this.sent = Counter.builder("notification_events_processed_total").register(registry);
    }

    @Scheduled(fixedDelayString = "${app.worker.fixed-delay-ms:5000}")
    @Transactional
    public void process() {
        var events = outbox.findUnprocessedNotificationEvents().stream().limit(batchSize).toList();

        for (var event : events) {
            String template = switch (event.getEventType()) {
                case "OrderCreated" -> "order-created";
                case "PaymentSucceeded" -> "payment-succeeded";
                case "PaymentFailed" -> "payment-failed";
                case "OrderCancelled" -> "order-cancelled";
                default -> "generic";
            };

            emailClient.sendTransactionalEmail(template, event.getPayloadJson());
            processed.save(new ProcessedNotificationEvent(event.getId()));
            sent.increment();
        }
    }
}

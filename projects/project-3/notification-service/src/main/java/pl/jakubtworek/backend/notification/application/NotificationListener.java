package pl.jakubtworek.backend.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import pl.jakubtworek.backend.common.events.OrderPaidEvent;
import pl.jakubtworek.backend.common.web.CorrelationId;
import pl.jakubtworek.backend.notification.config.RabbitConfig;
import pl.jakubtworek.backend.notification.chaos.NotificationChaosSettings;

@Component
public class NotificationListener {
    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);
    private final NotificationChaosSettings chaosSettings;
    private final Counter notificationSentCounter;

    public NotificationListener(NotificationChaosSettings chaosSettings,
                                MeterRegistry meterRegistry) {
        this.chaosSettings = chaosSettings;
        this.notificationSentCounter = Counter.builder("app_notifications_sent_total")
                .description("Notifications successfully processed by notification-service")
                .register(meterRegistry);
    }

    @RabbitListener(queues = RabbitConfig.NOTIFICATIONS_QUEUE)
    public void onOrderPaid(OrderPaidEvent event) throws InterruptedException {
        if (event.correlationId() != null) {
            MDC.put(CorrelationId.MDC_CORRELATION_ID, event.correlationId());
        }
        if (event.requestId() != null) {
            MDC.put(CorrelationId.MDC_REQUEST_ID, event.requestId());
        }
        if (event.traceId() != null) {
            MDC.put(CorrelationId.MDC_TRACE_ID, event.traceId());
        }
        try {
            long processingDelayMs = chaosSettings.processingDelayMs();
            if (processingDelayMs > 0) {
                log.warn("notification_processing_delay_simulated delayMs={}", processingDelayMs);
                Thread.sleep(processingDelayMs);
            }
            notificationSentCounter.increment();
            log.info("notification_sent orderId={} reservationId={} eventId={} userId={} amount={}",
                    event.orderId(), event.reservationId(), event.eventId(), event.userId(), event.amount());
        } finally {
            MDC.remove(CorrelationId.MDC_CORRELATION_ID);
            MDC.remove(CorrelationId.MDC_REQUEST_ID);
            MDC.remove(CorrelationId.MDC_TRACE_ID);
        }
    }
}

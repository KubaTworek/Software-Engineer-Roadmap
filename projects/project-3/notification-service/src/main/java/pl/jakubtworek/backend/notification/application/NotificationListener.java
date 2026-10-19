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
import pl.jakubtworek.backend.notification.persistence.ProcessedMessage;
import pl.jakubtworek.backend.notification.persistence.ProcessedMessageRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.amqp.core.Message;

import java.time.Clock;
import java.util.UUID;

@Component
public class NotificationListener {
    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);
    private final NotificationChaosSettings chaosSettings;
    private final Counter notificationSentCounter;
    private final ProcessedMessageRepository processedMessages;
    private final Clock clock;

    public NotificationListener(NotificationChaosSettings chaosSettings,
                                MeterRegistry meterRegistry,
                                ProcessedMessageRepository processedMessages,
                                Clock clock) {
        this.chaosSettings = chaosSettings;
        this.notificationSentCounter = Counter.builder("app_notifications_sent_total")
                .description("Notifications successfully processed by notification-service")
                .register(meterRegistry);
        this.processedMessages = processedMessages;
        this.clock = clock;
    }

    @RabbitListener(queues = RabbitConfig.NOTIFICATIONS_QUEUE)
    @Transactional
    public void onOrderPaid(OrderPaidEvent event, Message message) throws InterruptedException {
        String rawMessageId = message.getMessageProperties().getMessageId();
        UUID messageId = rawMessageId == null ? null : UUID.fromString(rawMessageId);
        if (messageId == null) {
            /* Fail closed: bez stabilnego ID nie da się bezpiecznie odróżnić redelivery. */
            throw new IllegalArgumentException("Order event has no messageId");
        }
        if (processedMessages.tryMarkProcessed(messageId, clock.instant()) == 0) {
            log.info("notification_duplicate_ignored messageId={} orderId={}", messageId, event.orderId());
            return;
        }
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

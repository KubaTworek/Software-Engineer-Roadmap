package pl.jakubtworek.backend.order.outbox;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.backend.order.config.RabbitConfig;
import pl.jakubtworek.backend.common.events.OrderPaidEvent;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Relay realizuje at-least-once delivery. Awaria po potwierdzeniu brokera, lecz przed
 * zapisem publishedAt, spowoduje duplikat — jest to oczekiwane i testowane po stronie inboxa.
 * W wielu replikach produkcyjnych rekordy pobiera się przez SKIP LOCKED albo lease.
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.outbox.enabled", matchIfMissing = true)
public class OutboxRelay {
    private final OutboxRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public OutboxRelay(OutboxRepository repository, RabbitTemplate rabbitTemplate, Clock clock,
                       ObjectMapper objectMapper) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    /** Gauge pokazuje zaległą pracę; rosnący backlog wykrywa awarię relaya przed użytkownikiem. */
    @org.springframework.beans.factory.annotation.Autowired
    void registerMetrics(MeterRegistry registry) {
        registry.gauge("app_outbox_unpublished", repository, OutboxRepository::countByPublishedAtIsNullAndDeadAtIsNull);
        /* Osobna metryka odróżnia przejściowy backlog od wiadomości wymagającej operatora. */
        registry.gauge("app_outbox_dead", repository, OutboxRepository::countByDeadAtIsNotNull);
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay:500ms}")
    @Transactional
    public void publishBatch() {
        for (OutboxMessage message : repository.lockBatchForPublishing(clock.instant())) {
            try {
                OrderPaidEvent event = readEvent(message);
                rabbitTemplate.invoke(operations -> {
                    operations.convertAndSend(RabbitConfig.ORDERS_EXCHANGE,
                            RabbitConfig.ORDER_PAID_ROUTING_KEY, event, amqp -> {
                                amqp.getMessageProperties().setMessageId(message.getId().toString());
                                amqp.getMessageProperties().setContentType("application/json");
                                return amqp;
                            });
                    /* Confirm oznacza przyjęcie przez brokera, nie obsługę przez konsumenta. */
                    operations.waitForConfirmsOrDie(3_000);
                    return true;
                });
                message.markPublished(clock.instant());
            } catch (Exception exception) {
                message.scheduleRetry(clock.instant(), exception.getMessage());
            }
        }
    }

    private OrderPaidEvent readEvent(OutboxMessage message) {
        try {
            return objectMapper.readValue(message.getPayload(), OrderPaidEvent.class);
        } catch (Exception exception) {
            /* Poison outbox nie może być bez końca mylony z awarią brokera. */
            throw new IllegalStateException("Invalid outbox payload " + message.getId(), exception);
        }
    }
}

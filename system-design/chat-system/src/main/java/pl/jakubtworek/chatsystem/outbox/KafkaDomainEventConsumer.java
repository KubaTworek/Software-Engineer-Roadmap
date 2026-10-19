package pl.jakubtworek.chatsystem.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Konsument prawdziwego brokera. Offset może zostać ponowiony po awarii, dlatego
 * każdy handler przechodzi przez własny trwały inbox w OutboxHandlerExecutor.
 */
@Component
@Profile("kafka")
public class KafkaDomainEventConsumer {
    private final ObjectMapper json;
    private final OutboxEventRepository events;
    private final List<DomainEventHandler> handlers;
    private final OutboxHandlerExecutor executor;

    public KafkaDomainEventConsumer(ObjectMapper json, OutboxEventRepository events,
                                    List<DomainEventHandler> handlers, OutboxHandlerExecutor executor) {
        this.json = json;
        this.events = events;
        this.handlers = handlers;
        this.executor = executor;
    }

    @KafkaListener(topics = "${app.outbox.kafka-topic:chat-domain-events}",
            groupId = "${app.outbox.kafka-consumer-group:chat-domain-handlers}")
    public void consume(String value) {
        try {
            KafkaEventBus.KafkaEnvelope envelope = json.readValue(value, KafkaEventBus.KafkaEnvelope.class);
            OutboxEvent event = events.findById(envelope.eventId())
                    .orElseThrow(() -> new IllegalStateException("Outbox event no longer exists"));
            for (DomainEventHandler handler : handlers) {
                if (handler.supports(event.getEventType())) executor.executeOnce(event, handler);
            }
            event.markPublished();
            events.save(event);
        } catch (RuntimeException ex) {
            // Wyjątek opuszcza listener, więc kontener nie zatwierdza offsetu i
            // rekord zostanie dostarczony ponownie. Inbox pomija ukończone handlery.
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid Kafka event envelope", ex);
        }
    }
}

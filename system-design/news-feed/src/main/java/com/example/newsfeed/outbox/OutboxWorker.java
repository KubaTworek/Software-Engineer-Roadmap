package com.example.newsfeed.outbox;

import com.example.newsfeed.events.KafkaEventPublisher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import io.micrometer.core.instrument.*;

import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Kanoniczny relay transactional outbox.
 *
 * <p>Transakcja biznesowa zapisuje rekord w {@code domain_events}. Relay
 * claimuje go krótkim lease, publikuje do Kafki i dopiero po potwierdzeniu
 * brokera oznacza jako przetworzony. Awaria po publikacji, lecz przed zmianą
 * statusu, może dać duplikat — dlatego każdy konsument ma własny inbox.
 * Jest to at-least-once delivery, a nie XA pomiędzy PostgreSQL i Kafką.</p>
 */
@Component
public class OutboxWorker {

    private static final Duration CLAIM_LEASE = Duration.ofSeconds(30);

    private final DomainEventRepository repository;
    private final KafkaEventPublisher kafka;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;
    private final int batchSize;
    private final Clock clock;
    private final Counter published;
    private final Counter failed;

    public OutboxWorker(
            DomainEventRepository repository,
            KafkaEventPublisher kafka,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            @Value("${newsfeed.workers.outbox.batch-size:50}") int batchSize,
            Clock clock,
            MeterRegistry meterRegistry
    ) {
        this.repository = repository;
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.transactions = new TransactionTemplate(transactionManager);
        this.batchSize = batchSize;
        this.clock = clock;
        this.published = meterRegistry.counter("newsfeed.outbox.publish", "result", "success");
        this.failed = meterRegistry.counter("newsfeed.outbox.publish", "result", "failure");
    }

    @Scheduled(fixedDelayString = "${newsfeed.workers.outbox.fixed-delay-ms:1000}")
    public void processDueEvents() {
        for (UUID id : repository.findDueEventIds(batchSize)) {
            claim(id).ifPresent(this::publishClaimed);
        }
    }

    private Optional<ClaimedEvent> claim(UUID id) {
        return transactions.execute(status -> repository.findByIdForUpdate(id)
                .filter(event -> event.getStatus() != DomainEventStatus.PROCESSED)
                .filter(event -> !event.getNextAttemptAt().isAfter(clock.instant()))
                .map(event -> {
                    event.claim(clock.instant(), CLAIM_LEASE);
                    return new ClaimedEvent(event.getId(), event.getEventType(), event.getAggregateId(),
                            event.getPayload(), event.getCreatedAt());
                }));
    }

    private void publishClaimed(ClaimedEvent claimed) {
        try {
            Map<String, String> attributes = new HashMap<>();
            Map<String, Object> payload = objectMapper.readValue(
                    claimed.payload(), new TypeReference<Map<String, Object>>() { });
            payload.forEach((key, value) -> attributes.put(key, String.valueOf(value)));

            UUID actorId = uuid(attributes.remove("actorId"), claimed.aggregateId());
            UUID entityId = uuid(attributes.remove("entityId"), claimed.aggregateId());
            Instant occurredAt = instant(attributes.remove("occurredAt"), claimed.createdAt());
            UUID originalEventId = uuid(attributes.remove("eventId"), claimed.id());

            var event = new com.example.newsfeed.events.DomainEvent(
                    originalEventId, claimed.topic(), actorId, entityId, occurredAt, Map.copyOf(attributes));
            kafka.publish(claimed.topic(), actorId.toString(), event);
            complete(claimed.id());
            published.increment();
        } catch (Exception exception) {
            fail(claimed.id(), exception);
            failed.increment();
        }
    }

    private void complete(UUID id) {
        transactions.executeWithoutResult(status -> repository.findByIdForUpdate(id).ifPresent(event -> {
            event.markProcessed(clock.instant());
            repository.save(event);
        }));
    }

    private void fail(UUID id, Exception exception) {
        transactions.executeWithoutResult(status -> repository.findByIdForUpdate(id).ifPresent(event -> {
            event.markFailed(exception, clock.instant());
            repository.save(event);
        }));
    }

    private UUID uuid(String value, UUID fallback) {
        return value == null ? fallback : UUID.fromString(value);
    }

    private Instant instant(String value, Instant fallback) {
        return value == null ? fallback : Instant.parse(value);
    }

    private record ClaimedEvent(UUID id, String topic, UUID aggregateId, String payload, Instant createdAt) { }
}

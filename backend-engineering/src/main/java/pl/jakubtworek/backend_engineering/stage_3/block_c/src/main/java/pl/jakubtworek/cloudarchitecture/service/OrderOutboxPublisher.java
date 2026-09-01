package pl.jakubtworek.cloudarchitecture.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.jakubtworek.cloudarchitecture.entity.OutboxEventEntity;
import pl.jakubtworek.cloudarchitecture.repository.OutboxEventRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Publishes durable outbox records with at-least-once delivery semantics. */
@Service
public class OrderOutboxPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderOutboxPublisher.class);
    private static final int MAX_ATTEMPTS = 10;

    private final OutboxEventRepository outboxRepository;
    private final PubSubPublisher pubSubPublisher;
    private final Clock clock;

    public OrderOutboxPublisher(
            OutboxEventRepository outboxRepository,
            PubSubPublisher pubSubPublisher
    ) {
        this(outboxRepository, pubSubPublisher, Clock.systemUTC());
    }

    public OrderOutboxPublisher(
            OutboxEventRepository outboxRepository,
            PubSubPublisher pubSubPublisher,
            Clock clock
    ) {
        this.outboxRepository = Objects.requireNonNull(outboxRepository, "outboxRepository must not be null");
        this.pubSubPublisher = Objects.requireNonNull(pubSubPublisher, "pubSubPublisher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.delay-ms:1000}")
    @Transactional
    public void publishPending() {
        // The laboratory keeps selected rows locked while publishing. This is
        // simple and prevents concurrent publishers from taking the same row,
        // but a slow broker extends the DB transaction. At larger scale prefer
        // a short claim/lease transaction, publish outside it, then finalize.
        Instant now = Instant.now(clock);
        for (OutboxEventEntity event : outboxRepository.lockNextPublishableBatch(now)) {
            try {
                pubSubPublisher.publishOrderCreated(event.getAggregateId());
                event.markPublished(now);
            } catch (RuntimeException exception) {
                event.recordFailedAttempt(now, MAX_ATTEMPTS);
                LOGGER.warn(
                        "outbox publish failed eventId={} aggregateId={} attempt={} errorType={}",
                        event.getId(),
                        event.getAggregateId(),
                        event.getAttempts(),
                        exception.getClass().getSimpleName()
                );
            }
            outboxRepository.save(event);
        }
    }
}

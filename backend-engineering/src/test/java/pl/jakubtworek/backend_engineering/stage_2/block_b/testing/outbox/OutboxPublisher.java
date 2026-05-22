package pl.jakubtworek.backend_engineering.stage_2.block_b.testing.outbox;

import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.broker.BrokerUnavailableException;
import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.broker.FakeKafkaBroker;

/**
 * Publisher responsible for draining the outbox and sending events to Kafka.
 *
 * If the broker is down, entries remain unpublished and can be retried later.
 */
public class OutboxPublisher {

    private final InMemoryOutbox outbox;
    private final FakeKafkaBroker broker;

    public OutboxPublisher(
            InMemoryOutbox outbox,
            FakeKafkaBroker broker
    ) {
        this.outbox = outbox;
        this.broker = broker;
    }

    /**
     * Attempts to publish all unpublished outbox entries.
     *
     * Entries are marked as published only after broker publication succeeds.
     */
    public void publishPendingEvents() {
        for (OutboxEntry entry : outbox.unpublishedEntries()) {
            try {
                broker.publish(entry.event());
                outbox.markPublished(entry.outboxId());
            } catch (BrokerUnavailableException exception) {
                /*
                 * The entry remains unpublished and will be retried later.
                 */
            }
        }
    }
}
package pl.jakubtworek.backend_engineering.stage_2.block_b.testing.kafka;

import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.consumer.SimulatedConsumerCrashException;
import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.events.OrderPlacedTestEvent;

/**
 * Test harness that simulates Kafka consumption and manual offset commit.
 *
 * It commits offset only when the handler finishes without exception.
 */
public class ManualCommitConsumerHarness {

    private final TestOffsetTracker offsetTracker;

    public ManualCommitConsumerHarness(TestOffsetTracker offsetTracker) {
        this.offsetTracker = offsetTracker;
    }

    /**
     * Processes one event and commits offset only after successful handling.
     */
    public void consume(
            OrderPlacedTestEvent event,
            long offset,
            EventConsumerFunction<OrderPlacedTestEvent> handler
    ) {
        try {
            handler.handle(event);
            offsetTracker.commit(offset + 1);
        } catch (SimulatedConsumerCrashException exception) {
            /*
             * The offset is intentionally not committed.
             * Kafka would redeliver this record after restart.
             */
        }
    }
}
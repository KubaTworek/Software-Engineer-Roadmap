package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.consumer;

import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.commit.ProcessingOutcome;
import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.commit.SafeOffsetCommitCoordinator;

/**
 * Coordinates record processing and manual offset commit.
 *
 * This class demonstrates the core consumer rule:
 * first process, then commit.
 */
public class ManualCommitKafkaConsumer<T> {

    private final KafkaRecordHandler<T> recordHandler;
    private final SafeOffsetCommitCoordinator commitCoordinator;

    public ManualCommitKafkaConsumer(
            KafkaRecordHandler<T> recordHandler,
            SafeOffsetCommitCoordinator commitCoordinator
    ) {
        this.recordHandler = recordHandler;
        this.commitCoordinator = commitCoordinator;
    }

    /**
     * Consumes one record and commits its offset only after a safe outcome.
     */
    public void consume(ConsumedKafkaRecord<T> record) {
        ProcessingOutcome outcome = recordHandler.handle(record);

        if (outcome.canCommitOffset()) {
            commitCoordinator.commitAfterProcessing(record.position(), outcome);
        }
    }
}
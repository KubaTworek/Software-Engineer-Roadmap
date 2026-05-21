package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.consumer;

import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.commit.ProcessingOutcome;

/**
 * Handles one consumed Kafka record.
 *
 * Business processing should happen before offset commit.
 */
public interface KafkaRecordHandler<T> {

    /**
     * Processes a consumed Kafka record.
     */
    ProcessingOutcome handle(
            ConsumedKafkaRecord<T> record
    );
}
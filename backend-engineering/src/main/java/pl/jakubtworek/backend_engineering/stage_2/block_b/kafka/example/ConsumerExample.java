package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.example;

import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.commit.ManualOffsetCommitter;
import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.commit.ProcessingOutcome;
import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.commit.SafeOffsetCommitCoordinator;
import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.consumer.ConsumedKafkaRecord;
import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.consumer.KafkaRecordHandler;
import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.consumer.KafkaRecordPosition;
import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.consumer.ManualCommitKafkaConsumer;
import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.events.PaymentAuthorized;

import java.time.Instant;
import java.util.UUID;

/**
 * Example showing manual offset commit after business processing.
 *
 * If the service crashes before commit, Kafka will deliver the record again.
 * Idempotent processing protects the system from duplicated side effects.
 */
public class ConsumerExample {

    public static void main(String[] args) {
        KafkaRecordHandler<PaymentAuthorized> handler = record -> {
            System.out.println("Processing event: " + record.value());

            /*
             * Business side effects should happen here:
             * - update database,
             * - publish outbox event,
             * - call internal application service.
             */

            return ProcessingOutcome.PROCESSED_SUCCESSFULLY;
        };

        ManualCommitKafkaConsumer<PaymentAuthorized> consumer =
                new ManualCommitKafkaConsumer<>(
                        handler,
                        new SafeOffsetCommitCoordinator(new ManualOffsetCommitter())
                );

        PaymentAuthorized event = new PaymentAuthorized(
                UUID.randomUUID(),
                Instant.now(),
                "ORD-12345",
                "ORD-12345",
                "PAY-999"
        );

        ConsumedKafkaRecord<PaymentAuthorized> record =
                new ConsumedKafkaRecord<>(
                        "ORD-12345",
                        event,
                        new KafkaRecordPosition("payments", 2, 42)
                );

        consumer.consume(record);
    }
}
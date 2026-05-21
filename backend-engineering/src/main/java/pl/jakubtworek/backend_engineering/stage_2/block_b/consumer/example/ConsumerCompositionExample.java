package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.example;

import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.OrderPlaced;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.deduplication.ProcessedEventRepository;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.dlq.KafkaDeadLetterPublisher;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.idempotency.DefaultIdempotentEventProcessor;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.idempotency.EventHandler;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.kafka.KafkaEventConsumer;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.kafka.KafkaRecordPosition;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.kafka.ManualOffsetCommitter;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.retry.ExponentialBackoffWithJitter;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.retry.RetryPolicy;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.retry.RetryingEventProcessor;

import java.time.Duration;

/**
 * Example showing how consumer components can be composed.
 *
 * The important architectural rule is that offset commit happens only after:
 * - successful processing,
 * - duplicate detection,
 * - or safe publication to DLQ.
 */
public class ConsumerCompositionExample {

    public KafkaEventConsumer<OrderPlaced> createConsumer(
            ProcessedEventRepository processedEventRepository,
            EventHandler<OrderPlaced> paymentHandler
    ) {
        DefaultIdempotentEventProcessor<OrderPlaced> idempotentProcessor =
                new DefaultIdempotentEventProcessor<>(
                        processedEventRepository,
                        paymentHandler
                );

        RetryPolicy retryPolicy = new RetryPolicy(
                5,
                new ExponentialBackoffWithJitter(
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(30),
                        2.0,
                        0.25
                )
        );

        RetryingEventProcessor<OrderPlaced> retryingProcessor =
                new RetryingEventProcessor<>(
                        retryPolicy,
                        idempotentProcessor::process
                );

        return new KafkaEventConsumer<>(
                retryingProcessor,
                new KafkaDeadLetterPublisher<>("orders.dlq"),
                new ManualOffsetCommitter()
        );
    }

    /**
     * Example of handling one already-deserialized Kafka record.
     */
    public void consumeOne(
            KafkaEventConsumer<OrderPlaced> consumer,
            OrderPlaced event
    ) {
        KafkaRecordPosition position = new KafkaRecordPosition(
                "orders",
                0,
                123
        );

        consumer.consume(event, position);
    }
}
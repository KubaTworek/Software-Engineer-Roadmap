package pl.jakubtworek.backend_engineering.stage_2.block_b.observability.example;

import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.EventMetadata;
import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.logging.ConsoleStructuredLogger;
import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.metrics.ConsoleMetricsRecorder;
import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.metrics.EventProcessingMetrics;
import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.processing.ObservableEventProcessor;
import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.tracing.ConsoleTraceRecorder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Example showing how logs, metrics and traces are attached to event processing.
 */
public class ObservabilityExample {

    public static void main(String[] args) {
        ObservableEventProcessor<OrderPlaced> processor =
                new ObservableEventProcessor<>(
                        event -> System.out.println("Processing order " + event.orderId()),
                        new ConsoleStructuredLogger(),
                        new EventProcessingMetrics(new ConsoleMetricsRecorder()),
                        new ConsoleTraceRecorder()
                );

        OrderPlaced event = new OrderPlaced(
                new EventMetadata(
                        UUID.randomUUID(),
                        Instant.now(),
                        "ORD-12345",
                        null,
                        "order-service",
                        UUID.randomUUID().toString()
                ),
                "ORD-12345",
                new BigDecimal("159.99")
        );

        processor.process(event);
    }
}
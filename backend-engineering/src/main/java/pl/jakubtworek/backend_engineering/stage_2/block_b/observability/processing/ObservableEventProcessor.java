package pl.jakubtworek.backend_engineering.stage_2.block_b.observability.processing;

import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.ObservableEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.context.ObservabilityContext;
import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.logging.StructuredLogger;
import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.metrics.EventProcessingMetrics;
import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.tracing.TraceRecorder;
import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.tracing.TraceSpan;

import java.util.Map;

/**
 * Wrapper that adds observability around event processing.
 *
 * Business handlers should focus on domain logic, while this wrapper handles
 * logs, metrics and traces.
 */
public class ObservableEventProcessor<T extends ObservableEvent> {

    private final EventHandler<T> eventHandler;
    private final StructuredLogger logger;
    private final EventProcessingMetrics metrics;
    private final TraceRecorder traceRecorder;

    public ObservableEventProcessor(
            EventHandler<T> eventHandler,
            StructuredLogger logger,
            EventProcessingMetrics metrics,
            TraceRecorder traceRecorder
    ) {
        this.eventHandler = eventHandler;
        this.logger = logger;
        this.metrics = metrics;
        this.traceRecorder = traceRecorder;
    }

    /**
     * Processes an event with logs, metrics and distributed tracing.
     */
    public void process(T event) {
        ObservabilityContext context = ObservabilityContext.from(
                event.metadata(),
                event.eventType(),
                event.aggregateId()
        );

        long startedAt = System.currentTimeMillis();

        logger.info(
                "Event processing started.",
                context,
                Map.of("stage", "start")
        );

        try (TraceSpan span = traceRecorder.startSpan("process " + event.eventType(), context)) {
            span.setAttribute("event.id", event.metadata().eventId().toString());
            span.setAttribute("event.type", event.eventType());
            span.setAttribute("correlation.id", event.metadata().correlationId());

            eventHandler.handle(event);

            long duration = System.currentTimeMillis() - startedAt;

            metrics.recordProcessed(context, duration);
            span.markSuccess();

            logger.info(
                    "Event processing completed.",
                    context,
                    Map.of("durationMs", duration)
            );
        } catch (RuntimeException exception) {
            metrics.recordFailed(context);

            logger.error(
                    "Event processing failed.",
                    context,
                    exception,
                    Map.of("stage", "failure")
            );

            throw exception;
        }
    }
}
package pl.jakubtworek.backend_engineering.stage_3.block_b.pipeline;

import pl.jakubtworek.backend_engineering.stage_3.block_b.structured_logs.StructuredLogEvent;

/** Receives a structured log event produced inside the active trace context. */
@FunctionalInterface
public interface TelemetryLogSink {

    void emit(StructuredLogEvent event);
}

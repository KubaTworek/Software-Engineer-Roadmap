package pl.jakubtworek.backend_engineering.stage_3.block_b.structured_logs;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Serializes structured log events to JSON.
 *
 * In production, this would usually be integrated with Logback, Log4j2,
 * an OpenTelemetry appender, or a centralized logging pipeline.
 */
public final class StructuredLogJsonSerializer {

    private final ObjectMapper objectMapper;

    public StructuredLogJsonSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(StructuredLogEvent event) {
        try {
            return objectMapper.writeValueAsString(event.toMap());
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize structured log event", exception);
        }
    }
}
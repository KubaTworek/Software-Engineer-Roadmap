package pl.jakubtworek.backend_engineering.stage_2.block_b.versioning.schema;

import java.time.Instant;

/**
 * Represents a registered schema version for a specific event type.
 *
 * In a real system, this information would usually be stored in Schema Registry,
 * not manually managed inside application code.
 */
public record RegisteredSchema(
        String subject,
        String eventType,
        int version,
        CompatibilityMode compatibilityMode,
        String schemaDefinition,
        Instant registeredAt
) {
    /**
     * Returns the registry subject name.
     *
     * In Kafka-based systems, a subject often follows conventions such as:
     * topic-name-value, topic-name-key, or fully qualified event name.
     */
    public String subjectName() {
        return subject;
    }
}
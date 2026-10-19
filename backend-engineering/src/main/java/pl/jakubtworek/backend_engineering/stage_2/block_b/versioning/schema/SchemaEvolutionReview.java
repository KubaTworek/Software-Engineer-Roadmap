package pl.jakubtworek.backend_engineering.stage_2.block_b.versioning.schema;

import java.util.List;

/**
 * Represents a high-level review of schema changes before deployment.
 *
 * This class is intentionally simple and is meant to show the theoretical rules
 * that teams often enforce during event contract evolution.
 */
public class SchemaEvolutionReview {

    private final CompatibilityMode compatibilityMode;

    public SchemaEvolutionReview(CompatibilityMode compatibilityMode) {
        if (compatibilityMode == null) {
            throw new IllegalArgumentException("Compatibility mode is required");
        }
        this.compatibilityMode = compatibilityMode;
    }

    /**
     * Reviews format-independent change heuristics.
     *
     * <p>A successful result is permission to continue to the real schema
     * compatibility check, not proof of Avro or Protobuf compatibility.</p>
     */
    public SchemaCompatibilityResult review(List<SchemaChange> changes) {
        if (changes == null) {
            throw new IllegalArgumentException("Schema changes cannot be null");
        }

        boolean hasUnsafeChange = changes.stream()
                .anyMatch(change -> !change.isUsuallySafe());

        if (hasUnsafeChange) {
            return SchemaCompatibilityResult.failure(
                    compatibilityMode,
                    "Schema contains potentially breaking changes. A migration plan is required."
            );
        }

        return SchemaCompatibilityResult.success(
                compatibilityMode,
                "Changes pass the high-level review; format-specific compatibility must still be verified."
        );
    }
}

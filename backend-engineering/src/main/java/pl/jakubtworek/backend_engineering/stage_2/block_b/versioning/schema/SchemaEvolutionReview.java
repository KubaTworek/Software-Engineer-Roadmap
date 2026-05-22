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
        this.compatibilityMode = compatibilityMode;
    }

    /**
     * Reviews a list of schema changes and returns whether they are acceptable.
     */
    public SchemaCompatibilityResult review(List<SchemaChange> changes) {
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
                "Schema changes appear safe for compatible evolution."
        );
    }
}
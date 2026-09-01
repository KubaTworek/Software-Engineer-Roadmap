package pl.jakubtworek.backend_engineering.stage_2.block_c.progressive_delivery;

import java.util.ArrayList;
import java.util.List;

/** Checks whether every simultaneously running revision supports the live schema. */
public final class SchemaCompatibilityValidator {

    public record ApplicationRevision(String name, int minimumSchema, int maximumSchema) {
        public ApplicationRevision {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("revision name is required");
            if (minimumSchema < 1 || maximumSchema < minimumSchema) throw new IllegalArgumentException("invalid schema range");
        }

        boolean supports(int schemaVersion) {
            return schemaVersion >= minimumSchema && schemaVersion <= maximumSchema;
        }
    }

    public List<String> validate(int liveSchemaVersion, List<ApplicationRevision> runningRevisions) {
        if (liveSchemaVersion < 1) throw new IllegalArgumentException("liveSchemaVersion must be positive");
        if (runningRevisions == null || runningRevisions.isEmpty()) {
            throw new IllegalArgumentException("at least one running revision is required");
        }
        List<String> violations = new ArrayList<>();
        for (ApplicationRevision revision : runningRevisions) {
            if (!revision.supports(liveSchemaVersion)) {
                violations.add(revision.name() + " does not support schema " + liveSchemaVersion);
            }
        }
        return List.copyOf(violations);
    }

    public boolean canRollback(int liveSchemaVersion, ApplicationRevision stableRevision) {
        return stableRevision.supports(liveSchemaVersion);
    }
}

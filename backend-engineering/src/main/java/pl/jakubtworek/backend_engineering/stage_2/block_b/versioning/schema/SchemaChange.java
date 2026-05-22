package pl.jakubtworek.backend_engineering.stage_2.block_b.versioning.schema;

/**
 * Describes a single schema change.
 *
 * This model can be used in tests, reviews, or documentation to classify
 * whether a schema modification is safe.
 */
public record SchemaChange(
        String fieldName,
        SchemaChangeType changeType,
        boolean hasDefaultValue,
        boolean optional
) {
    /**
     * Returns true when the change is usually safe for compatible evolution.
     *
     * Adding a new optional field with a default value is typically safe.
     * Removing or renaming fields is usually unsafe without a migration plan.
     */
    public boolean isUsuallySafe() {
        return switch (changeType) {
            case FIELD_ADDED -> optional || hasDefaultValue;
            case FIELD_REMOVED, FIELD_RENAMED, FIELD_TYPE_CHANGED -> false;
            case DOCUMENTATION_CHANGED -> true;
        };
    }
}
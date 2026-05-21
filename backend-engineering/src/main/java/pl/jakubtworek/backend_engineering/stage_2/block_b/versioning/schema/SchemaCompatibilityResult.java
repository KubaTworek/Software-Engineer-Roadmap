package pl.jakubtworek.backend_engineering.stage_2.block_b.versioning.schema;

/**
 * Represents the result of comparing two schema versions.
 *
 * The checker can use this result to decide whether a new schema can be safely
 * registered and deployed.
 */
public record SchemaCompatibilityResult(
        boolean compatible,
        CompatibilityMode checkedMode,
        String message
) {
    /**
     * Creates a successful compatibility result.
     */
    public static SchemaCompatibilityResult success(
            CompatibilityMode checkedMode,
            String message
    ) {
        return new SchemaCompatibilityResult(true, checkedMode, message);
    }

    /**
     * Creates a failed compatibility result.
     */
    public static SchemaCompatibilityResult failure(
            CompatibilityMode checkedMode,
            String message
    ) {
        return new SchemaCompatibilityResult(false, checkedMode, message);
    }
}
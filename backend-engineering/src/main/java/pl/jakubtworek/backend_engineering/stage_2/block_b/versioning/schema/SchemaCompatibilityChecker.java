package pl.jakubtworek.backend_engineering.stage_2.block_b.versioning.schema;

/**
 * Contract for checking whether a new schema is compatible with an existing schema.
 *
 * Real implementations would delegate this logic to Avro, Protobuf,
 * or Schema Registry APIs.
 */
public interface SchemaCompatibilityChecker {

    /**
     * Checks if the new schema is compatible with the previous schema
     * according to the selected compatibility mode.
     */
    SchemaCompatibilityResult checkCompatibility(
            RegisteredSchema previousSchema,
            RegisteredSchema newSchema,
            CompatibilityMode mode
    );
}
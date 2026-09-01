package pl.jakubtworek.backend_engineering.stage_2.block_b.versioning.schema;

/**
 * Simplified compatibility checker used for educational purposes.
 *
 * This class does not parse real Avro or Protobuf schemas.
 * It only shows where compatibility verification belongs in the design.
 */
public class SimpleSchemaCompatibilityChecker implements SchemaCompatibilityChecker {

    @Override
    public SchemaCompatibilityResult checkCompatibility(
            RegisteredSchema previousSchema,
            RegisteredSchema newSchema,
            CompatibilityMode mode
    ) {
        if (!previousSchema.eventType().equals(newSchema.eventType())) {
            return SchemaCompatibilityResult.failure(
                    mode,
                    "Schemas describe different event types."
            );
        }

        if (newSchema.version() <= previousSchema.version()) {
            return SchemaCompatibilityResult.failure(
                    mode,
                    "New schema version must be greater than the previous version."
            );
        }

        if (mode == CompatibilityMode.NONE) {
            return SchemaCompatibilityResult.success(
                    mode,
                    "Compatibility checks are disabled."
            );
        }

        // Do not manufacture a positive guarantee. Compatibility depends on
        // the serialization format and on reader/writer schema resolution.
        // A real Avro/Protobuf checker or Schema Registry must decide this.
        return SchemaCompatibilityResult.failure(
                mode,
                "Compatibility was not verified: delegate to an Avro/Protobuf checker or Schema Registry."
        );
    }
}

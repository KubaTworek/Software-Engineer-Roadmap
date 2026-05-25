package pl.jakubtworek.backend_engineering.stage_2.block_b.versioning.schema;

import java.util.List;

/**
 * Enforces internal team rules for public event schemas.
 *
 * These rules are not a replacement for Schema Registry compatibility checks.
 * They are an additional safety net used during code review or CI.
 */
public class EventSchemaPolicy {

    /**
     * Validates whether a proposed schema change follows safe evolution rules.
     */
    public SchemaCompatibilityResult validate(
            String eventType,
            List<SchemaChange> changes
    ) {
        for (SchemaChange change : changes) {
            if (change.changeType() == SchemaChangeType.FIELD_REMOVED) {
                return SchemaCompatibilityResult.failure(
                        CompatibilityMode.BACKWARD,
                        "Field removal in event " + eventType + " is not isAllowed without migration."
                );
            }

            if (change.changeType() == SchemaChangeType.FIELD_RENAMED) {
                return SchemaCompatibilityResult.failure(
                        CompatibilityMode.BACKWARD,
                        "Field rename in event " + eventType + " is treated as a breaking change."
                );
            }

            if (change.changeType() == SchemaChangeType.FIELD_TYPE_CHANGED) {
                return SchemaCompatibilityResult.failure(
                        CompatibilityMode.BACKWARD,
                        "Field type change in event " + eventType + " may break consumers."
                );
            }

            if (change.changeType() == SchemaChangeType.FIELD_ADDED
                    && !change.optional()
                    && !change.hasDefaultValue()) {
                return SchemaCompatibilityResult.failure(
                        CompatibilityMode.BACKWARD,
                        "New field " + change.fieldName()
                                + " must be optional or have a default value."
                );
            }
        }

        return SchemaCompatibilityResult.success(
                CompatibilityMode.BACKWARD,
                "Schema changes follow safe evolution rules for event " + eventType + "."
        );
    }
}
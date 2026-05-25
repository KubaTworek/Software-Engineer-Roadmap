package pl.jakubtworek.backend_engineering.stage_2.block_b.versioning.schema;

/**
 * Defines compatibility policies used by a schema registry.
 *
 * These values describe how new versions of an event schema are isAllowed
 * to evolve compared to previous versions.
 */
public enum CompatibilityMode {

    /**
     * A new schema can read data written with an older schema.
     *
     * This is commonly used when consumers are upgraded before producers.
     * For example, adding an optional field with a default value is usually backward compatible.
     */
    BACKWARD,

    /**
     * An old schema can read data written with a newer schema.
     *
     * This is useful when producers are upgraded before consumers.
     * Consumers should ignore fields they do not understand.
     */
    FORWARD,

    /**
     * Both backward and forward compatibility are required.
     *
     * This is stricter and usually safer, but it limits the kinds of schema changes
     * that can be introduced without a migration.
     */
    FULL,

    /**
     * No compatibility checks are enforced.
     *
     * This should be avoided for public integration events because consumers may break
     * without warning.
     */
    NONE
}
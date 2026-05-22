package pl.jakubtworek.backend_engineering.stage_2.block_b.versioning.schema;

/**
 * Classifies common schema modification types.
 */
public enum SchemaChangeType {

    /**
     * A new field was added to the schema.
     *
     * This is usually safe only when the field is optional or has a default value.
     */
    FIELD_ADDED,

    /**
     * An existing field was removed.
     *
     * This is usually a breaking change because older consumers or readers
     * may still expect this field.
     */
    FIELD_REMOVED,

    /**
     * An existing field was renamed.
     *
     * This is usually equivalent to removing the old field and adding a new one,
     * so it should be treated as a breaking change.
     */
    FIELD_RENAMED,

    /**
     * The type of an existing field was changed.
     *
     * This may break serialization or deserialization unless the format explicitly
     * supports the conversion.
     */
    FIELD_TYPE_CHANGED,

    /**
     * Only documentation, description, or comments were changed.
     *
     * This does not affect binary compatibility.
     */
    DOCUMENTATION_CHANGED
}
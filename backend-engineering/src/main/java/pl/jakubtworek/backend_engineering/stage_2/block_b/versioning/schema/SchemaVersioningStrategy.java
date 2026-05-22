package pl.jakubtworek.backend_engineering.stage_2.block_b.versioning.schema;

/**
 * Describes the strategy used to version an event contract.
 *
 * Versioning can be handled by changing the event name, by adding a version field,
 * or by relying on compatible schema evolution rules.
 */
public enum SchemaVersioningStrategy {

    /**
     * The event type name contains the version.
     *
     * Example: OrderPlacedV1, OrderPlacedV2.
     * This makes routing explicit, but it increases the number of event types.
     */
    VERSION_IN_EVENT_NAME,

    /**
     * The event schema contains a dedicated version field.
     *
     * Example: OrderPlaced with version = 2.
     * This keeps one logical event type but forces consumers to handle versions internally.
     */
    VERSION_FIELD,

    /**
     * The event name remains stable and the schema evolves in a compatible way.
     *
     * Example: OrderPlaced keeps the same name, but a new optional field is added.
     * This is usually the preferred approach when using Avro or Protobuf with Schema Registry.
     */
    COMPATIBLE_SCHEMA_EVOLUTION
}
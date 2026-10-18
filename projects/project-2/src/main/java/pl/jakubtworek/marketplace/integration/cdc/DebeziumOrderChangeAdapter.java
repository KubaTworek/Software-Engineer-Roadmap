package pl.jakubtworek.marketplace.integration.cdc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * Adapter prawdziwego envelope Debezium do modelu projekcji. Rozróżnia snapshot
 * ({@code r}), insert/update ({@code c/u}) i tombstone ({@code d}); metadane Kafki
 * są checkpointem transportu, podczas gdy PostgreSQL LSN pozostaje diagnostyką
 * źródła. Zmiana schematu lub brak klucza kończy rekord jako poison zamiast
 * zgadywać dane i przesuwać checkpoint.
 */
public final class DebeziumOrderChangeAdapter {
    private final ObjectMapper mapper;
    public DebeziumOrderChangeAdapter(ObjectMapper mapper) { this.mapper = mapper; }

    public CdcProjectionPipeline.Change<UUID, OrderProjection> fromKafka(
            int partition, long offset, String json) {
        try {
            JsonNode envelope = mapper.readTree(json);
            JsonNode payload = envelope.has("payload") ? envelope.get("payload") : envelope;
            String operation = required(payload, "op").stringValue();
            boolean deleted = operation.equals("d");
            if (!deleted && !operation.matches("[cru]")) {
                throw new IllegalArgumentException("unsupported Debezium operation: " + operation);
            }
            JsonNode row = payload.get(deleted ? "before" : "after");
            if (row == null || row.isNull()) throw new IllegalArgumentException("Debezium row image is missing");
            UUID id = UUID.fromString(required(row, "id").stringValue());
            OrderProjection value = deleted ? null : new OrderProjection(id, required(row, "status").stringValue());
            return new CdcProjectionPipeline.Change<>(
                    new CdcProjectionPipeline.Position(partition, offset), id, value, deleted);
        } catch (IllegalArgumentException expected) {
            throw expected;
        } catch (Exception malformed) {
            throw new IllegalArgumentException("invalid Debezium envelope", malformed);
        }
    }

    private JsonNode required(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) throw new IllegalArgumentException("missing field: " + field);
        return value;
    }

    public record OrderProjection(UUID orderId, String status) {}
}

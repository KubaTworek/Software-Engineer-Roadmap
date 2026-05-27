package pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.wide_column;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Przykład rekordu projektowanego pod Cassandrę / ScyllaDB.
 *
 * Access pattern:
 * "Pobierz ostatnie eventy użytkownika po userId, posortowane po czasie".
 */
public class UserEventRow {

    private final UUID userId;
    private final Instant eventTime;
    private final UUID eventId;
    private final String eventType;
    private final Map<String, String> payload;

    public UserEventRow(
            UUID userId,
            Instant eventTime,
            UUID eventId,
            String eventType,
            Map<String, String> payload
    ) {
        this.userId = userId;
        this.eventTime = eventTime;
        this.eventId = eventId;
        this.eventType = eventType;
        this.payload = Map.copyOf(payload);
    }

    public static String accessPattern() {
        return "Get user events by userId ordered by eventTime desc";
    }

    public UUID userId() { return userId; }
    public Instant eventTime() { return eventTime; }
    public UUID eventId() { return eventId; }
    public String eventType() { return eventType; }
    public Map<String, String> payload() { return payload; }
}

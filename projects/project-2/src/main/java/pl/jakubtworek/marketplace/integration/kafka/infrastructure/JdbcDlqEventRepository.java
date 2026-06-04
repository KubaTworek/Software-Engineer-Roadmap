package pl.jakubtworek.marketplace.integration.kafka.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import pl.jakubtworek.marketplace.integration.kafka.DlqEvent;
import pl.jakubtworek.marketplace.integration.kafka.DlqEventRepository;
import pl.jakubtworek.marketplace.integration.kafka.DlqEventStatus;
import pl.jakubtworek.marketplace.integration.kafka.IntegrationEventEnvelope;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementacja repozytorium DLQ.
 *
 * DLQ, czyli Dead Letter Queue, przechowuje eventy, których konsument nie był
 * w stanie poprawnie przetworzyć po wykorzystaniu dostępnych prób retry.
 *
 * Ta implementacja zapisuje DLQ trwale w PostgreSQL, dzięki czemu eventy błędne
 * nie znikają po restarcie aplikacji i mogą być później analizowane albo replayowane.
 */
@Repository
@Profile("postgres")
public class JdbcDlqEventRepository implements DlqEventRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcDlqEventRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    /**
     * Mapper wiersza z tabeli integration.dead_letter_events na obiekt DlqEvent.
     *
     * Envelope jest zapisany w bazie jako JSONB, a tutaj odtwarzamy go do
     * IntegrationEventEnvelope.
     */
    private final RowMapper<DlqEvent> rowMapper = (rs, rowNum) -> {
        IntegrationEventEnvelope envelope = readEnvelope(
                rs.getString("envelope")
        );

        return DlqEvent.restore(
                rs.getObject("id", UUID.class),
                rs.getString("topic"),
                rs.getString("consumer_group"),
                rs.getLong("kafka_offset"),
                envelope,
                rs.getString("reason"),
                rs.getInt("attempts"),
                rs.getTimestamp("failed_at").toInstant(),
                DlqEventStatus.valueOf(rs.getString("status")),
                timestampToInstant(rs.getTimestamp("replayed_at")),
                rs.getString("replay_error")
        );
    };

    /**
     * Zapisuje event DLQ.
     *
     * Używamy UPSERT-a, ponieważ ten sam obiekt DLQ może być później aktualizowany,
     * np. przy zmianie statusu na REPLAYED albo REPLAY_FAILED.
     */
    @Override
    public void save(DlqEvent event) {
        jdbcTemplate.update("""
                INSERT INTO integration.dead_letter_events (
                    id,
                    topic,
                    consumer_group,
                    kafka_offset,
                    envelope,
                    reason,
                    attempts,
                    failed_at,
                    status,
                    replayed_at,
                    replay_error
                )
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    topic = EXCLUDED.topic,
                    consumer_group = EXCLUDED.consumer_group,
                    kafka_offset = EXCLUDED.kafka_offset,
                    envelope = EXCLUDED.envelope,
                    reason = EXCLUDED.reason,
                    attempts = EXCLUDED.attempts,
                    failed_at = EXCLUDED.failed_at,
                    status = EXCLUDED.status,
                    replayed_at = EXCLUDED.replayed_at,
                    replay_error = EXCLUDED.replay_error
                """,
                event.id(),
                event.topic(),
                event.consumerGroup(),
                event.offset(),
                writeEnvelope(event.envelope()),
                event.reason(),
                event.attempts(),
                Timestamp.from(event.failedAt()),
                event.status().name(),
                event.replayedAt() == null ? null : Timestamp.from(event.replayedAt()),
                event.replayError()
        );
    }

    /**
     * Wyszukuje event DLQ po jego technicznym ID.
     */
    @Override
    public Optional<DlqEvent> findById(UUID id) {
        return jdbcTemplate.query("""
                        SELECT *
                        FROM integration.dead_letter_events
                        WHERE id = ?
                        """,
                rowMapper,
                id
        ).stream().findFirst();
    }

    /**
     * Zwraca eventy DLQ o konkretnym statusie.
     *
     * Wyniki są sortowane po czasie błędu, od najstarszego.
     */
    @Override
    public List<DlqEvent> findByStatus(DlqEventStatus status, int limit) {
        return jdbcTemplate.query("""
                        SELECT *
                        FROM integration.dead_letter_events
                        WHERE status = ?
                        ORDER BY failed_at ASC
                        LIMIT ?
                        """,
                rowMapper,
                status.name(),
                limit
        );
    }

    /**
     * Zwraca wszystkie eventy DLQ.
     *
     * Ten wariant jest zgodny z aktualnym interfejsem DlqEventRepository.
     * Docelowo lepiej zmienić interfejs na findAll(int limit), żeby nie pobierać
     * nieograniczonej liczby rekordów.
     */
    @Override
    public List<DlqEvent> findAll() {
        return jdbcTemplate.query("""
                        SELECT *
                        FROM integration.dead_letter_events
                        ORDER BY failed_at ASC
                        """,
                rowMapper
        );
    }

    /**
     * Metoda pomocnicza dla testów integracyjnych.
     */
    public void clear() {
        jdbcTemplate.update("DELETE FROM integration.dead_letter_events");
    }

    private String writeEnvelope(IntegrationEventEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot serialize DLQ envelope", e);
        }
    }

    private IntegrationEventEnvelope readEnvelope(String json) {
        try {
            return objectMapper.readValue(json, IntegrationEventEnvelope.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot deserialize DLQ envelope", e);
        }
    }

    private static Instant timestampToInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
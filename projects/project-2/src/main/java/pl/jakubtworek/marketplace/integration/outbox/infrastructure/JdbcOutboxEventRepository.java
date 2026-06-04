package pl.jakubtworek.marketplace.integration.outbox.infrastructure;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEvent;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventRepository;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventStatus;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementacja repozytorium outboxa.
 *
 * Ta klasa należy do warstwy infrastruktury modułu integration/outbox.
 * Odpowiada za trwały zapis i odczyt eventów z tabeli integration.outbox_events.
 *
 * Jest aktywna tylko dla profilu postgres, czyli wtedy, gdy aplikacja ma korzystać
 * z trwałego outboxa w PostgreSQL.
 *
 * Rola tego repozytorium:
 * - zapisuje eventy wygenerowane przez domenę,
 * - pozwala workerom pobierać eventy do publikacji,
 * - przechowuje status publikacji,
 * - zapisuje retry_count i last_error,
 * - umożliwia ręczne ponowienie publikacji eventu.
 *
 * Dzięki temu event nie ginie, jeśli aplikacja zapisze agregat, ale padnie przed
 * wysłaniem wiadomości do Kafki albo lokalnego dispatchera.
 */
@Profile("postgres")
@Repository
public class JdbcOutboxEventRepository implements OutboxEventRepository {

    /**
     * Springowy helper do wykonywania zapytań SQL.
     *
     * JdbcTemplate jest szczegółem infrastruktury. Nie powinien pojawiać się
     * w domenie ani w use case’ach.
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * Mapper pojedynczego wiersza z tabeli integration.outbox_events na obiekt OutboxEvent.
     *
     * OutboxEvent jest technicznym modelem eventu zapisanego w outboxie.
     * Payload pozostaje JSON-em jako String, a właściwa deserializacja do DomainEvent
     * dzieje się później w OutboxEventMapper.
     */
    private final RowMapper<OutboxEvent> rowMapper = (rs, rowNum) -> new OutboxEvent(
            rs.getObject("id", UUID.class),
            rs.getObject("aggregate_id", UUID.class),
            rs.getString("aggregate_type"),
            rs.getString("event_type"),
            rs.getInt("event_version"),
            rs.getString("payload"),
            rs.getObject("correlation_id", UUID.class),
            rs.getObject("causation_id", UUID.class),
            OutboxEventStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("created_at").toInstant(),
            timestampToInstant(rs.getTimestamp("published_at")),
            rs.getInt("retry_count"),
            rs.getString("last_error")
    );

    public JdbcOutboxEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Zapisuje event do outboxa.
     *
     * Używamy UPSERT-a:
     * - jeśli event o danym ID nie istnieje, zostaje dodany,
     * - jeśli istnieje, aktualizujemy jego status i pola związane z publikacją.
     *
     * Payload zapisujemy jako jsonb, żeby PostgreSQL walidował poprawność JSON-a
     * i umożliwiał ewentualne późniejsze filtrowanie po polach payloadu.
     *
     * W typowym flow insert powinien wydarzyć się w tej samej transakcji co zapis agregatu.
     */
    @Override
    public void save(OutboxEvent event) {
        jdbcTemplate.update("""
                INSERT INTO integration.outbox_events (
                    id,
                    aggregate_id,
                    aggregate_type,
                    event_type,
                    event_version,
                    payload,
                    correlation_id,
                    causation_id,
                    status,
                    created_at,
                    published_at,
                    retry_count,
                    last_error
                )
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    status = EXCLUDED.status,
                    published_at = EXCLUDED.published_at,
                    retry_count = EXCLUDED.retry_count,
                    last_error = EXCLUDED.last_error
                """,
                event.id(),
                event.aggregateId(),
                event.aggregateType(),
                event.eventType(),
                event.eventVersion(),
                event.payload(),
                event.correlationId(),
                event.causationId(),
                event.status().name(),
                Timestamp.from(event.createdAt()),
                event.publishedAt() == null ? null : Timestamp.from(event.publishedAt()),
                event.retryCount(),
                event.lastError()
        );
    }

    /**
     * Wyszukuje event outboxowy po ID.
     *
     * Zwracamy Optional, ponieważ event o podanym ID może nie istnieć.
     * Ta metoda jest używana m.in. przez endpointy administracyjne oraz ręczne retry.
     */
    @Override
    public Optional<OutboxEvent> findById(UUID eventId) {
        return jdbcTemplate.query("""
                        SELECT *
                        FROM integration.outbox_events
                        WHERE id = ?
                        """,
                rowMapper,
                eventId
        ).stream().findFirst();
    }

    /**
     * Zwraca eventy niezależnie od statusu.
     *
     * Wyniki są sortowane po created_at rosnąco, żeby najstarsze eventy były widoczne
     * jako pierwsze.
     *
     * Ta metoda jest używana głównie do diagnostyki przez endpoint administracyjny.
     */
    @Override
    public List<OutboxEvent> findAll(int limit) {
        return jdbcTemplate.query("""
                        SELECT *
                        FROM integration.outbox_events
                        ORDER BY created_at ASC
                        LIMIT ?
                        """,
                rowMapper,
                limit
        );
    }

    /**
     * Zwraca eventy o konkretnym statusie.
     *
     * Przykładowe statusy:
     * - NEW — event czeka na publikację,
     * - PUBLISHED — event został opublikowany,
     * - FAILED — publikacja zakończyła się błędem.
     *
     * Workery używają tej metody do pobierania eventów oczekujących na publikację
     * albo do ponawiania eventów błędnych.
     */
    @Override
    public List<OutboxEvent> findByStatus(OutboxEventStatus status, int limit) {
        return jdbcTemplate.query("""
                        SELECT *
                        FROM integration.outbox_events
                        WHERE status = ?
                        ORDER BY created_at ASC
                        LIMIT ?
                        """,
                rowMapper,
                status.name(),
                limit
        );
    }

    /**
     * Oznacza event jako opublikowany.
     *
     * Ustawiamy:
     * - status = PUBLISHED,
     * - published_at = aktualny czas,
     * - last_error = NULL.
     *
     * retry_count zostaje bez zmian, żeby zachować historię wcześniejszych prób.
     */
    @Override
    public void markPublished(UUID eventId) {
        jdbcTemplate.update("""
                UPDATE integration.outbox_events
                SET
                    status = ?,
                    published_at = ?,
                    last_error = NULL
                WHERE id = ?
                """,
                OutboxEventStatus.PUBLISHED.name(),
                Timestamp.from(Instant.now()),
                eventId
        );
    }

    /**
     * Oznacza event jako zakończony błędem.
     *
     * Zwiększamy retry_count i zapisujemy powód błędu.
     * Dzięki temu operator albo test może sprawdzić, dlaczego event nie został opublikowany.
     */
    @Override
    public void markFailed(UUID eventId, String reason) {
        jdbcTemplate.update("""
                UPDATE integration.outbox_events
                SET
                    status = ?,
                    retry_count = retry_count + 1,
                    last_error = ?
                WHERE id = ?
                """,
                OutboxEventStatus.FAILED.name(),
                reason,
                eventId
        );
    }

    /**
     * Przywraca event do statusu NEW, żeby mógł zostać ponownie przetworzony.
     *
     * Nie pozwalamy przywrócić do NEW eventu już opublikowanego.
     * Dzięki temu ręczne retry nie powinno przypadkowo zdublować eventu, który został
     * skutecznie opublikowany wcześniej.
     */
    @Override
    public void markNewForRetry(UUID eventId) {
        jdbcTemplate.update("""
                UPDATE integration.outbox_events
                SET status = ?
                WHERE id = ?
                  AND status <> ?
                """,
                OutboxEventStatus.NEW.name(),
                eventId,
                OutboxEventStatus.PUBLISHED.name()
        );
    }

    /**
     * Pomocnicza metoda zamieniająca nullable Timestamp na nullable Instant.
     *
     * published_at może być null, jeśli event nie został jeszcze opublikowany.
     */
    private static Instant timestampToInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
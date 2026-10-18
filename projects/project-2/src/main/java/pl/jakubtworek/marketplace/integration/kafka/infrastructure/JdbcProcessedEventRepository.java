package pl.jakubtworek.marketplace.integration.kafka.infrastructure;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pl.jakubtworek.marketplace.integration.kafka.ProcessedEvent;
import pl.jakubtworek.marketplace.integration.kafka.ProcessedEventRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementacja repozytorium processed_events.
 *
 * Repozytorium processed_events służy do idempotencji konsumentów Kafki.
 * Dzięki niemu konsument może sprawdzić, czy konkretny event został już
 * przetworzony przez konkretnego konsumenta.
 *
 * To jest krytyczne w modelu at-least-once delivery:
 * - Kafka może dostarczyć ten sam event więcej niż raz,
 * - konsument może wykonać efekt uboczny,
 * - aplikacja może paść przed commitem offsetu,
 * - po restarcie ten sam event zostanie dostarczony ponownie.
 *
 * Wtedy wpis w processed_events pozwala pominąć ponowne wykonanie efektu ubocznego.
 */
@Repository
@Profile("postgres")
public class JdbcProcessedEventRepository implements ProcessedEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcProcessedEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Sprawdza, czy dany event został już przetworzony przez wskazanego konsumenta.
     */
    @Override
    public boolean exists(UUID eventId, String consumerName) {
        Boolean exists = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM integration.processed_events
                            WHERE event_id = ?
                              AND consumer_name = ?
                        )
                        """,
                Boolean.class,
                eventId,
                consumerName
        );

        return Boolean.TRUE.equals(exists);
    }

    /**
     * Pobiera wpis processed_events dla eventu i konsumenta.
     *
     * Zwracamy Optional, ponieważ event mógł nie zostać jeszcze przetworzony.
     */
    @Override
    public Optional<ProcessedEvent> find(UUID eventId, String consumerName) {
        List<ProcessedEvent> events = jdbcTemplate.query("""
                        SELECT
                            event_id,
                            consumer_name,
                            processed_at
                        FROM integration.processed_events
                        WHERE event_id = ?
                          AND consumer_name = ?
                        """,
                (rs, rowNum) -> new ProcessedEvent(
                        rs.getObject("event_id", UUID.class),
                        rs.getString("consumer_name"),
                        rs.getTimestamp("processed_at").toInstant()
                ),
                eventId,
                consumerName
        );

        return events.stream().findFirst();
    }

    /**
     * Zapisuje informację o przetworzeniu eventu.
     *
     * Nie używamy tutaj {@code ON CONFLICT DO NOTHING}. Gdy dwa egzemplarze
     * konsumenta równolegle przejdą wcześniejsze sprawdzenie {@code exists}, konflikt
     * unikalnego klucza ma wycofać całą przegrywającą transakcję — razem z efektem
     * biznesowym i wpisami outboxa. Ciche zignorowanie konfliktu zatwierdziłoby efekt
     * wykonany drugi raz.
     */
    @Override
    public void save(ProcessedEvent processedEvent) {
        jdbcTemplate.update("""
                INSERT INTO integration.processed_events (
                    event_id,
                    consumer_name,
                    processed_at
                )
                VALUES (?, ?, ?)
                """,
                processedEvent.eventId(),
                processedEvent.consumerName(),
                Timestamp.from(processedEvent.processedAt())
        );
    }

    /**
     * Metoda pomocnicza dla testów integracyjnych.
     *
     * Nie musi być częścią interfejsu ProcessedEventRepository.
     */
    public void clear() {
        jdbcTemplate.update("DELETE FROM integration.processed_events");
    }
}

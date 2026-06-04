package pl.jakubtworek.marketplace.integration.outbox.infrastructure;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEvent;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventRepository;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventStatus;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementacja repozytorium outboxa.
 *
 * Ta klasa należy do warstwy infrastruktury modułu integration/outbox.
 * Implementuje port OutboxEventRepository, ale nie zapisuje danych trwale.
 *
 * Jest użyteczna w:
 * - testach jednostkowych i komponentowych,
 * - lokalnym uruchomieniu aplikacji bez PostgreSQL,
 * - wcześniejszych fazach projektu, zanim zostanie podłączona trwała baza danych.
 *
 * Nie jest to implementacja produkcyjna:
 * - eventy znikają po restarcie aplikacji,
 * - nie ma blokad bazodanowych,
 * - nie ma izolacji transakcji,
 * - nie odwzorowuje w pełni zachowania PostgreSQL.
 */
@Profile("!postgres")
@Repository
public class InMemoryOutboxEventRepository implements OutboxEventRepository {

    /**
     * Prosty magazyn eventów w pamięci procesu.
     *
     * Kluczem jest eventId, a wartością OutboxEvent.
     *
     * ConcurrentHashMap zapewnia podstawowe bezpieczeństwo przy równoległym dostępie
     * do samej mapy, ale nie rozwiązuje problemów atomowego pobierania eventów
     * przez wielu workerów jednocześnie.
     */
    private final Map<UUID, OutboxEvent> events = new ConcurrentHashMap<>();

    /**
     * Zapisuje event w outboxie.
     *
     * Jeśli event o tym samym ID już istnieje, zostanie nadpisany.
     *
     * W produkcyjnej implementacji JDBC zapis powinien odbywać się w tej samej transakcji
     * co zmiana agregatu, który wygenerował zdarzenie.
     */
    @Override
    public void save(OutboxEvent event) {
        events.put(event.id(), event);
    }

    /**
     * Wyszukuje event po ID.
     *
     * Zwracamy Optional, ponieważ event o podanym ID może nie istnieć.
     */
    @Override
    public Optional<OutboxEvent> findById(UUID eventId) {
        return Optional.ofNullable(events.get(eventId));
    }

    /**
     * Zwraca wszystkie eventy, niezależnie od statusu.
     *
     * Wyniki są sortowane po createdAt, żeby najstarsze eventy były widoczne jako pierwsze.
     * Parametr limit ogranicza liczbę zwracanych rekordów.
     *
     * Ta metoda jest używana głównie przez endpoint administracyjny do podglądu outboxa.
     */
    @Override
    public List<OutboxEvent> findAll(int limit) {
        return events.values().stream()
                .sorted(Comparator.comparing(OutboxEvent::createdAt))
                .limit(limit)
                .toList();
    }

    /**
     * Zwraca eventy o konkretnym statusie.
     *
     * Przykładowe statusy:
     * - NEW — event czeka na publikację,
     * - PUBLISHED — event został opublikowany,
     * - FAILED — publikacja zakończyła się błędem.
     *
     * Wyniki są sortowane po createdAt, żeby worker przetwarzał eventy w kolejności
     * ich powstania.
     */
    @Override
    public List<OutboxEvent> findByStatus(OutboxEventStatus status, int limit) {
        return events.values().stream()
                .filter(event -> event.status() == status)
                .sorted(Comparator.comparing(OutboxEvent::createdAt))
                .limit(limit)
                .toList();
    }

    /**
     * Oznacza event jako opublikowany.
     *
     * Jeśli event nie istnieje, metoda nic nie robi.
     *
     * W implementacji bazodanowej warto byłoby dodatkowo sprawdzać aktualny status,
     * żeby uniknąć przypadkowego oznaczenia niepoprawnego eventu jako PUBLISHED.
     */
    @Override
    public void markPublished(UUID eventId) {
        events.computeIfPresent(
                eventId,
                (id, event) -> event.markPublished(java.time.Instant.now())
        );
    }

    /**
     * Oznacza event jako zakończony błędem.
     *
     * reason zawiera przyczynę błędu, np. problem z deserializacją, handlerem,
     * brokerem albo nieobsługiwanym typem eventu.
     *
     * W OutboxEvent.markFailed(...) powinien zostać zwiększony retryCount
     * oraz zapisany lastError.
     */
    @Override
    public void markFailed(UUID eventId, String reason) {
        events.computeIfPresent(
                eventId,
                (id, event) -> event.markFailed(reason)
        );
    }

    /**
     * Przywraca event do stanu NEW, żeby można było ponowić jego publikację.
     *
     * Ta operacja jest używana przy ręcznym retry z endpointu administracyjnego
     * albo przez mechanizm retryFailed(...).
     */
    @Override
    public void markNewForRetry(UUID eventId) {
        events.computeIfPresent(
                eventId,
                (id, event) -> event.markNewForRetry()
        );
    }
}
package pl.jakubtworek.marketplace.integration.kafka.infrastructure;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import pl.jakubtworek.marketplace.integration.kafka.DlqEvent;
import pl.jakubtworek.marketplace.integration.kafka.DlqEventRepository;
import pl.jakubtworek.marketplace.integration.kafka.DlqEventStatus;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementacja repozytorium DLQ.
 *
 * DLQ, czyli Dead Letter Queue, przechowuje eventy, których konsument nie był
 * w stanie poprawnie przetworzyć po wykorzystaniu dostępnych prób retry.
 *
 * Przykładowe powody trafienia eventu do DLQ:
 * - nieobsługiwana wersja eventu,
 * - błędny payload,
 * - błąd deserializacji,
 * - trwały błąd biznesowy,
 * - wyjątek rzucony przez handler,
 * - problem, którego retry nie rozwiązał.
 *
 * Ta implementacja przechowuje dane tylko w pamięci procesu.
 * Jest przydatna w testach i lokalnym trybie bez PostgreSQL, ale nie nadaje się
 * jako produkcyjne repozytorium DLQ.
 */
@Repository
@Profile("!postgres")
public class InMemoryDlqEventRepository implements DlqEventRepository {

    /**
     * Magazyn eventów DLQ w pamięci.
     *
     * Kluczem jest identyfikator technicznego wpisu DLQ.
     * Wartością jest DlqEvent zawierający oryginalny envelope, topic, offset,
     * consumer group, powód błędu i status replay.
     */
    private final ConcurrentHashMap<UUID, DlqEvent> events = new ConcurrentHashMap<>();

    /**
     * Zapisuje event w DLQ.
     *
     * Jeśli event o tym samym ID już istnieje, zostanie nadpisany.
     *
     * W praktyce ta metoda jest używana:
     * - gdy konsument przekroczy maksymalną liczbę prób,
     * - gdy replay zmienia status eventu na REPLAYED albo REPLAY_FAILED.
     */
    @Override
    public void save(DlqEvent event) {
        events.put(event.id(), event);
    }

    /**
     * Wyszukuje event DLQ po jego identyfikatorze.
     *
     * Zwracamy Optional, ponieważ event o podanym ID może nie istnieć.
     * Ta metoda jest używana m.in. przez endpoint administracyjny replay.
     */
    @Override
    public Optional<DlqEvent> findById(UUID id) {
        return Optional.ofNullable(events.get(id));
    }

    /**
     * Zwraca eventy DLQ o konkretnym statusie.
     *
     * Przykładowe statusy:
     * - NEW — event trafił do DLQ i nie był jeszcze replayowany,
     * - REPLAYED — replay zakończył się sukcesem,
     * - REPLAY_FAILED — replay zakończył się błędem.
     *
     * Wyniki są sortowane po failedAt, żeby najstarsze błędy były widoczne jako pierwsze.
     * Parametr limit zabezpiecza przed zwróceniem zbyt dużej liczby rekordów.
     */
    @Override
    public List<DlqEvent> findByStatus(DlqEventStatus status, int limit) {
        return events.values().stream()
                .filter(event -> event.status() == status)
                .sorted(Comparator.comparing(DlqEvent::failedAt))
                .limit(limit)
                .toList();
    }

    /**
     * Zwraca wszystkie eventy DLQ.
     *
     * Ta metoda jest używana przez endpoint administracyjny.
     *
     * Uwaga:
     * w produkcyjnej implementacji lepiej mieć wariant z limitem, np. findAll(int limit),
     * żeby przypadkowo nie zwrócić bardzo dużej liczby rekordów.
     */
    @Override
    public List<DlqEvent> findAll() {
        return events.values().stream()
                .sorted(Comparator.comparing(DlqEvent::failedAt))
                .toList();
    }

    /**
     * Czyści wszystkie eventy DLQ.
     *
     * Metoda pomocnicza dla testów, żeby odizolować scenariusze testowe.
     */
    public void clear() {
        events.clear();
    }
}
package pl.jakubtworek.marketplace.integration.kafka.infrastructure;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import pl.jakubtworek.marketplace.integration.kafka.ProcessedEvent;
import pl.jakubtworek.marketplace.integration.kafka.ProcessedEventRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementacja repozytorium processed_events.
 *
 * Repozytorium processed_events służy do idempotencji konsumentów Kafki.
 * Dzięki niemu konsument może sprawdzić, czy dany event został już przetworzony
 * przez konkretnego konsumenta.
 *
 * To jest ważne, ponieważ Kafka może dostarczyć tę samą wiadomość więcej niż raz,
 * np. gdy:
 * - konsument przetworzył event,
 * - zapisał zmianę w bazie,
 * - zapisał wpis processed_events,
 * - ale aplikacja padła przed commitem offsetu.
 *
 * Po restarcie Kafka może dostarczyć ten sam event ponownie.
 * Wtedy konsument powinien wykryć wpis w processed_events i pominąć ponowne wykonanie
 * efektów ubocznych.
 *
 * Ta implementacja jest przeznaczona do testów i lokalnego trybu bez PostgreSQL.
 * Nie jest produkcyjna, bo dane znikają po restarcie aplikacji.
 */
@Repository
@Profile("!postgres")
public class InMemoryProcessedEventRepository implements ProcessedEventRepository {

    /**
     * Mapa przechowująca przetworzone eventy.
     *
     * Kluczem technicznym jest połączenie:
     * eventId + consumerName.
     *
     * Dzięki temu ten sam event może zostać przetworzony przez różnych konsumentów,
     * ale tylko raz przez tego samego konsumenta.
     */
    private final Map<String, ProcessedEvent> events = new ConcurrentHashMap<>();

    /**
     * Sprawdza, czy event został już przetworzony przez konkretnego konsumenta.
     *
     * Ta metoda jest używana przed wykonaniem logiki biznesowej.
     * Jeśli zwróci true, konsument powinien:
     * - nie uruchamiać handlerów ponownie,
     * - commitować offset,
     * - oznaczyć event jako duplikat w observability.
     */
    @Override
    public boolean exists(UUID eventId, String consumerName) {
        return events.containsKey(key(eventId, consumerName));
    }

    /**
     * Pobiera wpis processed_events dla konkretnego eventu i konsumenta.
     *
     * Zwracamy Optional, ponieważ event mógł nie być jeszcze przetworzony.
     */
    @Override
    public Optional<ProcessedEvent> find(UUID eventId, String consumerName) {
        return Optional.ofNullable(
                events.get(key(eventId, consumerName))
        );
    }

    /**
     * Zapisuje informację, że event został przetworzony przez konkretnego konsumenta.
     *
     * W implementacji produkcyjnej taki zapis powinien być odporny na duplikaty,
     * np. przez unikalny klucz w bazie:
     * (event_id, consumer_name).
     */
    @Override
    public void save(ProcessedEvent processedEvent) {
        events.put(
                key(processedEvent.eventId(), processedEvent.consumerName()),
                processedEvent
        );
    }

    /**
     * Czyści wszystkie wpisy.
     *
     * Metoda pomocnicza przydatna w testach, żeby odizolować scenariusze testowe.
     */
    public void clear() {
        events.clear();
    }

    /**
     * Buduje techniczny klucz mapy.
     *
     * Format:
     * eventId::consumerName
     */
    private String key(UUID eventId, String consumerName) {
        return eventId + "::" + consumerName;
    }
}
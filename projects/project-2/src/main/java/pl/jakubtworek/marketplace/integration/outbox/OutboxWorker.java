package pl.jakubtworek.marketplace.integration.outbox;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.shared.events.ApplicationEventBus;
import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;

import java.util.UUID;

/**
 * Worker publikujący zdarzenia zapisane w outboxie.
 *
 * Ta klasa należy do technicznego modułu integration/outbox.
 * Jej zadaniem jest pobieranie zdarzeń zapisanych wcześniej w tabeli outbox_events
 * i przekazywanie ich dalej do lokalnego ApplicationEventBus.
 *
 * Ten worker jest użyteczny głównie w fazie 3, czyli zanim zdarzenia zaczną być
 * publikowane do Kafki.
 *
 * Przepływ w fazie 3:
 * - use case zapisuje agregat,
 * - OutboxEventPublisher zapisuje zdarzenie do outboxa,
 * - OutboxWorker pobiera zdarzenie z outboxa,
 * - OutboxWorker odtwarza DomainEvent,
 * - ApplicationEventBus wywołuje odpowiednie handlery modułów,
 * - OutboxWorker oznacza event jako PUBLISHED albo FAILED.
 *
 * Po fazie 4 odpowiedzialność publikacji do Kafki powinna przejść do KafkaOutboxWorker.
 */
@Profile("stage3")
@Component
public class OutboxWorker {

    /**
     * Domyślny maksymalny rozmiar paczki eventów pobieranych z outboxa.
     *
     * Worker nie powinien pobierać nieograniczonej liczby zdarzeń naraz,
     * ponieważ mogłoby to przeciążyć aplikację albo spowodować zbyt długą transakcję.
     */
    private static final int DEFAULT_BATCH_SIZE = 50;

    /**
     * Repozytorium outboxa.
     *
     * Odpowiada za pobieranie eventów o statusie NEW/FAILED oraz zmianę ich statusu
     * na PUBLISHED albo FAILED.
     */
    private final OutboxEventRepository repository;

    /**
     * Lokalny event bus aplikacyjny.
     *
     * W fazie 3 worker publikuje zdarzenia do handlerów wewnątrz tego samego monolitu.
     * To nadal nie jest Kafka — wszystko dzieje się synchronicznie w jednym procesie JVM.
     */
    private final ApplicationEventBus eventBus;

    /**
     * Mapper odtwarzający DomainEvent z rekordu OutboxEvent.
     *
     * Outbox przechowuje zdarzenie jako dane techniczne, np. eventType, payload,
     * correlationId i causationId. Mapper zamienia ten zapis z powrotem na konkretny
     * obiekt domenowy.
     */
    private final OutboxEventMapper mapper;

    /**
     * Konstruktor używany przez Springa.
     *
     * Wstrzykujemy OutboxEventMapper jako bean, zamiast tworzyć go ręcznie z ObjectMappera.
     * Dzięki temu unikamy kilku publicznych konstruktorów i problemów z DI.
     */
    public OutboxWorker(
            OutboxEventRepository repository,
            ApplicationEventBus eventBus,
            OutboxEventMapper mapper
    ) {
        this.repository = repository;
        this.eventBus = eventBus;
        this.mapper = mapper;
    }

    /**
     * Cyklicznie publikuje nowe eventy z outboxa.
     *
     * fixedDelay oznacza, że kolejny przebieg wystartuje określony czas po zakończeniu
     * poprzedniego przebiegu.
     *
     * Wartość można skonfigurować przez:
     * marketplace.outbox.worker-delay-ms
     */
    @Scheduled(fixedDelayString = "${marketplace.outbox.worker-delay-ms:5000}")
    public void scheduledPublish() {
        publishNew(DEFAULT_BATCH_SIZE);
    }

    /**
     * Publikuje paczkę eventów o statusie NEW.
     *
     * Metoda zwraca liczbę pobranych eventów, niekoniecznie liczbę skutecznie
     * opublikowanych. Jeżeli część eventów zakończy się błędem, zostaną oznaczone
     * jako FAILED.
     */
    @Transactional
    public int publishNew(int limit) {
        var events = repository.findNew(limit);

        events.forEach(this::publishOneSafely);

        return events.size();
    }

    /**
     * Ponawia publikację eventów oznaczonych jako FAILED.
     *
     * To prosta forma retry. W bardziej produkcyjnej wersji warto dodać:
     * - retryCount,
     * - maxRetryCount,
     * - backoff,
     * - nextAttemptAt,
     * - klasyfikację błędów trwałych i tymczasowych.
     */
    @Transactional
    public int retryFailed(int limit) {
        var events = repository.findFailed(limit);

        events.forEach(this::publishOneSafely);

        return events.size();
    }

    /**
     * Ręcznie ponawia publikację konkretnego eventu outboxowego.
     *
     * Najpierw oznaczamy event jako NEW, żeby był traktowany jak gotowy do ponownego
     * przetworzenia, a następnie publikujemy go po ID.
     *
     * Taki mechanizm przydaje się w endpointach administracyjnych.
     */
    @Transactional
    public void retryManually(UUID outboxEventId) {
        repository.markNewForRetry(outboxEventId);
        publishById(outboxEventId);
    }

    /**
     * Publikuje konkretny event z outboxa po ID.
     *
     * Jeśli event jest już oznaczony jako PUBLISHED, metoda nic nie robi.
     * Dzięki temu operacja jest częściowo idempotentna na poziomie statusu outboxa.
     */
    @Transactional
    public void publishById(UUID outboxEventId) {
        OutboxEvent event = repository.findById(outboxEventId)
                .orElseThrow(() -> new IllegalArgumentException("Outbox event not found: " + outboxEventId));

        if (event.status() == OutboxEventStatus.PUBLISHED) {
            return;
        }

        publishOneSafely(event);
    }

    /**
     * Pomocnicza metoda dla testów i developmentu.
     *
     * Publikuje eventy z outboxa aż do momentu, gdy outbox będzie pusty,
     * albo do osiągnięcia limitu iteracji.
     *
     * Jest to przydatne, ponieważ obsługa jednego eventu może wygenerować kolejne eventy.
     *
     * Przykład:
     * - OrderPlaced zostaje opublikowany,
     * - handler Payment generuje PaymentReserved,
     * - handler Inventory generuje StockReserved,
     * - handler Ordering może wygenerować OrderConfirmed.
     *
     * Bez takiej pętli test musiałby ręcznie wywoływać publishNew(...) kilka razy.
     */
    public int publishUntilIdle(int batchSize, int maxIterations) {
        int total = 0;

        for (int i = 0; i < maxIterations; i++) {
            int published = publishNew(batchSize);
            total += published;

            if (published == 0) {
                return total;
            }
        }

        throw new IllegalStateException(
                "Outbox is still producing new events after " + maxIterations + " iterations"
        );
    }

    /**
     * Próbuje opublikować pojedynczy event.
     *
     * Jeśli publikacja się uda:
     * - event zostaje oznaczony jako PUBLISHED.
     *
     * Jeśli wystąpi wyjątek:
     * - event zostaje oznaczony jako FAILED,
     * - zapisywany jest komunikat błędu.
     *
     * Ta metoda celowo nie rzuca wyjątku dalej, żeby jeden uszkodzony event nie blokował
     * przetwarzania całej paczki.
     */
    private void publishOneSafely(OutboxEvent outboxEvent) {
        try {
            DomainEvent domainEvent = mapper.toDomainEvent(outboxEvent);

            eventBus.publish(domainEvent);

            repository.markPublished(outboxEvent.id());
        } catch (Exception e) {
            repository.markFailed(outboxEvent.id(), e.getMessage());
        }
    }
}
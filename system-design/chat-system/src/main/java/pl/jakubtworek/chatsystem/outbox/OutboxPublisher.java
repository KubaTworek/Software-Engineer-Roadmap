package pl.jakubtworek.chatsystem.outbox;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Worker odpowiedzialny za publikowanie nieobsłużonych eventów z outboxa.
 *
 * OutboxService tylko zapisuje event do bazy.
 * Ta klasa odpowiada za kolejny krok:
 * - znaleźć eventy gotowe do publikacji,
 * - oznaczyć je jako przekazane do kolejki,
 * - wrzucić je do EventBus.
 *
 * Dzięki temu główna ścieżka biznesowa, np. wysłanie wiadomości,
 * nie musi bezpośrednio odpalać WebSocketów, pushy, indeksowania search itd.
 *
 * Flow:
 * 1. MessageService zapisuje wiadomość.
 * 2. MessageService zapisuje MESSAGE_CREATED do outboxa.
 * 3. OutboxPublisher cyklicznie pobiera event z outboxa.
 * 4. OutboxPublisher przekazuje event do EventBus.
 * 5. Osobny consumer/handler wykonuje skutki uboczne.
 */
@Component
public class OutboxPublisher {
    private final String workerId = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();

    /**
     * Repozytorium outbox_events.
     *
     * Używane do znalezienia eventów,
     * które jeszcze nie zostały przekazane do kolejki
     * i nie przekroczyły maksymalnej liczby prób.
     */
    private final OutboxClaimService claims;

    /**
     * Lokalna abstrakcja kolejki/event busa.
     *
     * W obecnej wersji może to być in-memory queue.
     * W produkcji ten boundary można podmienić na Kafka, RabbitMQ, NATS itd.
     */
    private final EventBus eventBus;

    /**
     * Maksymalna liczba eventów pobieranych w jednym przebiegu workera.
     *
     * Chroni aplikację przed próbą przetworzenia zbyt wielu eventów naraz.
     */
    private final int batchSize;

    /**
     * Maksymalna liczba prób publikacji eventu.
     *
     * Eventy, które przekroczą ten limit, nie powinny być pobierane dalej
     * przez findPublishable. W praktyce wymagają później inspekcji albo dead-letter flow.
     */
    private final int maxAttempts;

    public OutboxPublisher(
            OutboxClaimService claims,
            EventBus eventBus,
            @Value("${app.outbox.batch-size:100}") int batchSize,
            @Value("${app.outbox.max-attempts:5}") int maxAttempts
    ) {
        this.claims = claims;
        this.eventBus = eventBus;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    /**
     * Cyklicznie publikuje eventy oczekujące w outboxie.
     *
     * Harmonogram:
     * app.outbox.publish-delay-ms, domyślnie 500 ms.
     *
     * fixedDelay oznacza:
     * kolejny przebieg zacznie się dopiero po zakończeniu poprzedniego
     * i odczekaniu wskazanego opóźnienia.
     *
     * To jest bezpieczniejsze niż fixedRate,
     * bo nie odpali równolegle kilku przebiegów tego samego workera
     * na jednej instancji, jeśli poprzedni przebieg się opóźni.
     */
    @Scheduled(fixedDelayString = "${app.outbox.publish-delay-ms:500}")
    public void publishPendingEvents() {

        /*
         * Pobieramy małą paczkę eventów gotowych do publikacji.
         *
         * findPublishable powinno zwracać eventy:
         * - jeszcze nieopublikowane,
         * - nieprzekroczone maxAttempts,
         * - najlepiej uporządkowane po createdAt/id.
         */
        // claim() kończy własną transakcję przed kontaktem z brokerem. Consumer
        // nigdy nie zobaczy eventu szybciej niż baza zatwierdzi stan ENQUEUED.
        var events = claims.claim(maxAttempts, batchSize, workerId);

        for (OutboxEvent event : events) {

            /*
             * Oznaczamy event jako przekazany do kolejki.
             *
             * To zapobiega wielokrotnemu pobieraniu tego samego eventu
             * przez kolejne przebiegi publishera.
             *
             * W bardziej zaawansowanej wersji warto rozróżnić stany:
             * NEW, ENQUEUED, PROCESSING, PUBLISHED, FAILED.
             */
            /*
             * Przekazujemy event do EventBus.
             *
             * Od tego momentu dalsza obsługa jest asynchroniczna:
             * WebSocket delivery, push notifications, search indexing itd.
             */
            try {
                eventBus.enqueue(event);
            } catch (RuntimeException ex) {
                claims.publicationFailed(event.getId(), ex.getMessage());
            }
        }
    }
}

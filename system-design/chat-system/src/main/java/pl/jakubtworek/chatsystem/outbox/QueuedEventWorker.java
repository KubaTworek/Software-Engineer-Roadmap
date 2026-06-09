package pl.jakubtworek.chatsystem.outbox;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Worker konsumujący eventy z lokalnej kolejki EventBus.
 *
 * W tym projekcie flow wygląda tak:
 *
 * 1. MessageService zapisuje zmianę biznesową, np. nową wiadomość.
 * 2. OutboxService dopisuje event do tabeli outbox_events.
 * 3. OutboxPublisher pobiera event z outboxa i wrzuca go do InMemoryQueueEventBus.
 * 4. QueuedEventWorker pobiera event z kolejki.
 * 5. Worker przekazuje event do handlerów, np. WebSocket delivery, push notifications, search indexing.
 * 6. Po udanym przetworzeniu oznacza event jako published.
 *
 * Ta klasa oddziela główną ścieżkę zapisu od skutków ubocznych.
 * Dzięki temu wysłanie wiadomości nie musi czekać synchronicznie
 * na powiadomienia, WebSocket broadcast albo indeksowanie search.
 */
@Component
public class QueuedEventWorker {

    /**
     * Lokalna kolejka eventów.
     *
     * To implementacja in-memory, dobra dla developmentu i wersji lokalnej.
     * W produkcji ten element powinien zostać zastąpiony zewnętrznym brokerem,
     * np. Kafka, RabbitMQ albo NATS.
     */
    private final InMemoryQueueEventBus queue;

    /**
     * Lista handlerów domenowych dostępnych w aplikacji.
     *
     * Spring automatycznie wstrzykuje wszystkie beany implementujące DomainEventHandler.
     *
     * Każdy handler sam deklaruje, czy obsługuje dany eventType,
     * np. "message.created".
     */
    private final List<DomainEventHandler> handlers;

    /**
     * Repozytorium outbox_events.
     *
     * Worker aktualizuje status eventu po przetworzeniu:
     * - published, jeśli wszystko się udało,
     * - failed, jeśli któryś handler rzucił wyjątek.
     */
    private final OutboxEventRepository repository;

    public QueuedEventWorker(
            InMemoryQueueEventBus queue,
            List<DomainEventHandler> handlers,
            OutboxEventRepository repository
    ) {
        this.queue = queue;
        this.handlers = handlers;
        this.repository = repository;
    }

    /**
     * Cyklicznie konsumuje eventy z lokalnej kolejki.
     *
     * Harmonogram:
     * app.outbox.consume-delay-ms, domyślnie 100 ms.
     *
     * fixedDelay oznacza, że kolejny przebieg ruszy dopiero
     * po zakończeniu poprzedniego i odczekaniu podanego opóźnienia.
     */
    @Scheduled(fixedDelayString = "${app.outbox.consume-delay-ms:100}")
    @Transactional
    public void consume() {
        OutboxEvent event;

        /*
         * Limit eventów w jednym przebiegu workera.
         *
         * Bez tego, jeśli kolejka byłaby cały czas pełna,
         * jeden przebieg mógłby trwać bardzo długo i blokować scheduler.
         */
        int processed = 0;

        /*
         * Pobieramy eventy z kolejki tak długo, jak:
         * - kolejka nie jest pusta,
         * - nie przekroczyliśmy limitu 200 eventów na jeden przebieg.
         *
         * queue.poll() jest nieblokujące.
         * Jeśli kolejka jest pusta, zwraca null.
         */
        while ((event = queue.poll()) != null && processed < 200) {
            try {
                /*
                 * Przekazujemy event do wszystkich handlerów,
                 * które deklarują obsługę jego typu.
                 *
                 * Przykład:
                 * eventType = "message.created"
                 *
                 * Mogą go obsłużyć:
                 * - realtime handler,
                 * - notification handler,
                 * - search index handler,
                 * - analytics handler.
                 */
                for (DomainEventHandler handler : handlers) {
                    if (handler.supports(event.getEventType())) {
                        handler.handle(event);
                    }
                }

                /*
                 * Jeśli wszystkie pasujące handlery zakończyły się sukcesem,
                 * oznaczamy event jako opublikowany/przetworzony.
                 */
                event.markPublished();

            } catch (RuntimeException ex) {
                /*
                 * Jeśli którykolwiek handler rzuci wyjątek,
                 * cały event oznaczamy jako failed.
                 *
                 * To proste podejście.
                 * W bardziej rozbudowanej architekturze warto rozważyć:
                 * - retry per handler,
                 * - dead-letter queue,
                 * - osobny status dla każdego handlera,
                 * - rozdzielenie eventu na osobne kolejki konsumentów.
                 */
                event.markFailed(ex.getMessage());
            }

            /*
             * Zapisujemy nowy stan eventu w outbox_events.
             *
             * Dzięki temu można monitorować,
             * które eventy zostały przetworzone,
             * a które zakończyły się błędem.
             */
            repository.save(event);

            processed++;
        }
    }
}
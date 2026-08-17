package pl.jakubtworek.chatsystem.outbox;

import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Lokalna, pamięciowa implementacja EventBus.
 *
 * Ta klasa działa jako prosta kolejka między:
 * - OutboxPublisher, który pobiera eventy z tabeli outbox_events,
 * - workerem/consumerem, który później te eventy obsługuje.
 *
 * W obecnej wersji eventy nie trafiają jeszcze do zewnętrznej infrastruktury
 * typu Kafka, RabbitMQ albo NATS.
 *
 * To jest świadomy kompromis:
 * - projekt da się łatwo uruchomić lokalnie,
 * - architektura ma już boundary EventBus,
 * - w przyszłości można podmienić tę klasę na adapter do prawdziwej kolejki.
 *
 * Ważne ograniczenie:
 * kolejka jest in-memory, więc jej zawartość znika po restarcie aplikacji.
 * Źródłem prawdy nadal pozostaje tabela outbox_events.
 */
@Component
public class InMemoryQueueEventBus implements EventBus {

    /**
     * Lokalna kolejka eventów.
     *
     * BlockingQueue jest thread-safe, więc może być używana równolegle przez:
     * - producerów, którzy dodają eventy,
     * - consumerów, którzy je pobierają.
     *
     * Limit 20_000 chroni aplikację przed niekontrolowanym zużyciem pamięci,
     * jeśli konsumenci nie nadążają z przetwarzaniem eventów.
     */
    private final BlockingQueue<OutboxEvent> queue = new LinkedBlockingQueue<>(20_000);

    /**
     * Dodaje event do lokalnej kolejki.
     *
     * Wywoływane zwykle przez OutboxPublisher po pobraniu eventu z outboxa.
     *
     * Używamy offer(), a nie put():
     * - offer() nie blokuje wątku,
     * - od razu zwraca false, jeśli kolejka jest pełna,
     * - dzięki temu aplikacja nie zawiesi workera na zawsze.
     */
    @Override
    public void enqueue(OutboxEvent event) {
        boolean accepted = queue.offer(event);

        /*
         * Jeśli kolejka jest pełna, rzucamy błąd.
         *
         * To jest sygnał przeciążenia systemu:
         * eventy są produkowane szybciej, niż consumer potrafi je obsłużyć.
         *
         * W produkcji powinno to uruchomić alert,
         * a docelowo warto mieć zewnętrzny broker z backpressure i DLQ.
         */
        if (!accepted) {
            throw new IllegalStateException("Local event queue is full");
        }
    }

    /**
     * Pobiera pojedynczy event z kolejki.
     *
     * poll() nie blokuje.
     * Jeśli kolejka jest pusta, zwraca null.
     *
     * Ta metoda ma package-private visibility,
     * bo powinna być używana tylko przez lokalny worker w tym samym pakiecie,
     * a nie przez dowolną część aplikacji.
     */
    OutboxEvent poll() {
        return queue.poll();
    }

    /**
     * Zwraca aktualny rozmiar kolejki.
     *
     * Przydatne do monitoringu i metryk.
     *
     * Jeśli size() stale rośnie, oznacza to,
     * że worker nie nadąża z przetwarzaniem eventów
     * albo któryś handler eventów jest zbyt wolny.
     */
    public int size() {
        return queue.size();
    }
}
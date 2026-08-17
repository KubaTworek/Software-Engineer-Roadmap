package pl.jakubtworek.chatsystem.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Komponent rejestrujący metryki Micrometer dla outboxa i lokalnej kolejki eventów.
 *
 * Te metryki są ważne operacyjnie, bo outbox jest krytycznym elementem
 * asynchronicznego przetwarzania zdarzeń.
 *
 * Jeśli outbox albo kolejka zaczynają rosnąć, oznacza to zwykle, że:
 * - OutboxPublisher nie nadąża z przekazywaniem eventów do kolejki,
 * - QueuedEventWorker nie nadąża z obsługą eventów,
 * - któryś handler eventów jest zbyt wolny,
 * - występują błędy w przetwarzaniu eventów,
 * - aplikacja jest przeciążona.
 *
 * Metryki zarejestrowane tutaj mogą być eksportowane przez Actuator/Prometheus.
 */
@Component
public class OutboxMetrics {

    /**
     * Rejestruje gauge metryki dla outboxa i lokalnej kolejki.
     *
     * Gauge to metryka pokazująca aktualną wartość w danym momencie,
     * np. aktualną liczbę eventów oczekujących albo rozmiar kolejki.
     *
     * W przeciwieństwie do Countera, Gauge może rosnąć i maleć.
     */
    public OutboxMetrics(
            OutboxEventRepository repository,
            InMemoryQueueEventBus queue,
            MeterRegistry registry
    ) {
        /*
         * Liczba eventów w statusie NEW.
         *
         * NEW oznacza, że event został zapisany do outboxa,
         * ale nie został jeszcze przekazany do kolejki przez OutboxPublisher.
         *
         * Jeśli ta wartość stale rośnie, to prawdopodobnie:
         * - OutboxPublisher nie działa,
         * - scheduler nie jest uruchomiony,
         * - findPublishable nie zwraca eventów,
         * - baza danych jest zbyt wolna,
         * - eventy są produkowane szybciej niż publisher je przenosi.
         */
        Gauge.builder(
                        "chat_outbox_new_total",
                        repository,
                        repo -> repo.countByStatus(OutboxStatus.NEW)
                )
                .description("Number of new outbox events waiting for enqueue")
                .register(registry);

        /*
         * Liczba eventów w statusie FAILED.
         *
         * FAILED oznacza, że event został pobrany z kolejki,
         * ale jego obsługa zakończyła się błędem.
         *
         * To jedna z najważniejszych metryk alarmowych.
         * Wzrost tej wartości oznacza, że część skutków ubocznych
         * nie została wykonana, np.:
         * - WebSocket broadcast,
         * - powiadomienia push,
         * - indeksowanie wyszukiwarki,
         * - analytics.
         */
        Gauge.builder(
                        "chat_outbox_failed_total",
                        repository,
                        repo -> repo.countByStatus(OutboxStatus.FAILED)
                )
                .description("Number of failed outbox events")
                .register(registry);

        /*
         * Aktualny rozmiar lokalnej kolejki eventów.
         *
         * Ta kolejka znajduje się w pamięci aplikacji.
         * Trafiają do niej eventy po pobraniu z outboxa,
         * zanim zostaną obsłużone przez QueuedEventWorker.
         *
         * Jeśli rozmiar kolejki stale rośnie, worker nie nadąża
         * albo handlery eventów są za wolne.
         */
        Gauge.builder(
                        "chat_local_event_queue_size",
                        queue,
                        InMemoryQueueEventBus::size
                )
                .description("Current local event queue size")
                .register(registry);
    }
}
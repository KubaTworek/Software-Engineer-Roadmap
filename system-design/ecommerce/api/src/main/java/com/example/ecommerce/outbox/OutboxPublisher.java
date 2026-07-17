package com.example.ecommerce.outbox;

import com.example.ecommerce.monitoring.BusinessMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Worker odpowiedzialny za publikowanie eventów zapisanych w tabeli outbox.
 *
 * OutboxService tylko zapisuje eventy do bazy.
 * OutboxPublisher cyklicznie pobiera nowe eventy i próbuje je opublikować.
 *
 * W tej wersji projektu publikacja jest mockiem:
 * - event jest logowany,
 * - status zmienia się na PUBLISHED,
 * - metryka outboxPublished zostaje zwiększona.
 *
 * Produkcyjnie w tym miejscu można podłączyć np.:
 * - Kafka,
 * - RabbitMQ,
 * - AWS SNS/SQS,
 * - Google Pub/Sub,
 * - webhook dispatcher,
 * - dedykowany event bus.
 */
@Component
@ConditionalOnProperty(
        prefix = "app.outbox",
        name = "publisher-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OutboxPublisher {

    /**
     * Logger techniczny.
     *
     * W MVP logowanie pełni rolę mock publikacji eventu.
     * Dzięki temu w logach widać, jakie eventy zostałyby wysłane dalej.
     */
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    /**
     * Repozytorium eventów outbox.
     *
     * Worker pobiera z niego eventy w statusie NEW
     * i aktualizuje ich status po próbie publikacji.
     */
    private final OutboxEventRepository events;

    /**
     * Metryki biznesowo-techniczne.
     *
     * Pozwalają obserwować, ile eventów outbox zostało poprawnie opublikowanych.
     * To ważne dla monitoringu integracji asynchronicznych.
     */
    private final BusinessMetrics metrics;

    /**
     * Constructor injection.
     *
     * Publisher potrzebuje repozytorium eventów i metryk.
     */
    public OutboxPublisher(
            OutboxEventRepository events,
            BusinessMetrics metrics
    ) {
        this.events = events;
        this.metrics = metrics;
    }

    /**
     * Cyklicznie publikuje oczekujące eventy outbox.
     *
     * Harmonogram:
     *
     * app.outbox.fixed-delay-ms=5000
     *
     * Domyślnie worker uruchamia się co 5 sekund po zakończeniu poprzedniego cyklu.
     *
     * @Transactional:
     * pobranie eventów, zmiana statusu na PUBLISHED/FAILED i zapis metryk
     * dzieją się w jednej transakcji bazy aplikacji.
     */
    @Scheduled(fixedDelayString = "${app.outbox.fixed-delay-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        /*
         * Pobieramy maksymalnie 50 najstarszych eventów w statusie NEW.
         *
         * Limit chroni aplikację przed zbyt dużym batch processingiem
         * w jednym cyklu workera.
         *
         * Sortowanie po ID daje prostą kolejność FIFO:
         * starsze eventy są publikowane przed nowszymi.
         */
        for (OutboxEvent event : events.findTop50ByStatusOrderByIdAsc(OutboxEventStatus.NEW)) {
            try {
                /*
                 * Mock publikacji eventu.
                 *
                 * W produkcyjnej wersji ten fragment zostałby zastąpiony
                 * realnym wysłaniem eventu do brokera lub event busa.
                 *
                 * Payload jest już JSON-em zapisanym przez OutboxService.
                 */
                log.info(
                        "OUTBOX_PUBLISH eventId={}, aggregateType={}, aggregateId={}, eventType={}, payload={}",
                        event.getId(),
                        event.getAggregateType(),
                        event.getAggregateId(),
                        event.getEventType(),
                        event.getPayloadJson()
                );

                /*
                 * Po udanej publikacji oznaczamy event jako PUBLISHED.
                 *
                 * Dzięki temu worker nie opublikuje go ponownie w następnym cyklu.
                 */
                event.markPublished();

                /*
                 * Metryka udanej publikacji.
                 *
                 * Przydatna do dashboardu i alertów, np. gdy liczba publikowanych
                 * eventów nagle spada albo rośnie backlog NEW.
                 */
                metrics.outboxPublished();
            } catch (Exception ex) {
                /*
                 * Jeśli publikacja się nie uda, oznaczamy event jako FAILED.
                 *
                 * Zapisujemy komunikat błędu, żeby dało się później diagnozować problem.
                 *
                 * W produkcyjnym systemie można dodać:
                 * - retry count,
                 * - nextRetryAt,
                 * - dead letter queue,
                 * - ręczny reprocess z panelu admina.
                 */
                event.markFailed(ex.getMessage());

                log.warn(
                        "OUTBOX_PUBLISH_FAILED eventId={}, error={}",
                        event.getId(),
                        ex.getMessage()
                );
            }
        }
    }
}
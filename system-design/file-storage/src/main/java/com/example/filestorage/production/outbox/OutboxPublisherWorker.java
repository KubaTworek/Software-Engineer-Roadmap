package com.example.filestorage.production.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Worker publikujący eventy z transactional outboxa.
 *
 * Transactional outbox rozwiązuje problem spójności między:
 * - zapisem danych domenowych w bazie,
 * - publikacją eventu do zewnętrznego systemu.
 *
 * Zamiast publikować event bezpośrednio w trakcie operacji biznesowej,
 * aplikacja zapisuje go do tabeli outbox w tej samej transakcji co zmianę domenową.
 *
 * Ten worker później pobiera eventy PENDING i publikuje je dalej.
 *
 * W tej implementacji publikacja jest lokalna i sprowadza się do logowania.
 * W realnej produkcji miejsce log.info(...) powinno zostać zastąpione producerem
 * do Kafki, SQS, Pub/Sub, RabbitMQ albo innego brokera.
 */
@Component
public class OutboxPublisherWorker {

    /**
     * Logger techniczny workera.
     *
     * Aktualnie pełni rolę lokalnego "publishera",
     * bo eventy są tylko logowane.
     */
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherWorker.class);

    /**
     * Repozytorium eventów outboxa.
     *
     * Worker pobiera stąd eventy w statusie PENDING
     * i zmienia ich status na PUBLISHED, FAILED albo ustawia retry.
     */
    private final OutboxEventRepository outboxRepository;

    /**
     * Repozytorium dead-letter queue.
     *
     * Jeśli event przekroczy maksymalną liczbę prób publikacji,
     * trafia do DLQ razem z informacją o błędzie.
     */
    private final DeadLetterEventRepository deadLetterRepository;

    /**
     * Konfiguracja outboxa.
     *
     * Zawiera np. maxAttempts, czyli maksymalną liczbę prób publikacji eventu.
     */
    private final OutboxProperties properties;

    public OutboxPublisherWorker(OutboxEventRepository outboxRepository,
                                 DeadLetterEventRepository deadLetterRepository,
                                 OutboxProperties properties) {
        this.outboxRepository = outboxRepository;
        this.deadLetterRepository = deadLetterRepository;
        this.properties = properties;
    }

    /**
     * Cyklicznie publikuje eventy PENDING z outboxa.
     *
     * Harmonogram:
     * app.production.outbox.publish-fixed-delay-ms
     *
     * Domyślnie worker uruchamia się co 5 sekund.
     *
     * Jednorazowo pobiera maksymalnie 50 najstarszych eventów.
     * Kolejność po createdAt daje prosty model FIFO.
     *
     * Uwaga produkcyjna:
     * ta metoda ma jedną transakcję na całą paczkę eventów.
     * Przy wolnym brokerze albo dużych payloadach lepszy model to transakcja per event
     * oraz mechanizm claim/lock, żeby kilka instancji workera nie publikowało tego samego eventu.
     */
    @Scheduled(fixedDelayString = "${app.production.outbox.publish-fixed-delay-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        /*
         * Pobieramy pierwszą stronę eventów PENDING.
         * Limit 50 chroni worker przed zbyt długą jednorazową pracą.
         */
        var events = outboxRepository.findAllByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                PageRequest.of(0, 50)
        );

        for (OutboxEvent event : events) {
            try {
                /*
                 * Lokalna implementacja publishera.
                 *
                 * W tym projekcie event jest tylko logowany, co wystarcza
                 * do lokalnego stage/demo.
                 *
                 * W produkcji ten fragment powinien zostać zastąpiony przez:
                 * - kafkaTemplate.send(...),
                 * - sqsClient.sendMessage(...),
                 * - pubSubTemplate.publish(...),
                 * - rabbitTemplate.convertAndSend(...).
                 */
                log.info(
                        "Publishing outbox event type={} aggregateType={} aggregateId={} payload={}",
                        event.getEventType(),
                        event.getAggregateType(),
                        event.getAggregateId(),
                        event.getPayload()
                );

                /*
                 * Jeśli publikacja się udała, event przechodzi w PUBLISHED.
                 */
                event.markPublished();

            } catch (Exception ex) {
                /*
                 * Normalizujemy komunikat błędu.
                 * Jeśli exception nie ma message, zapisujemy nazwę klasy wyjątku.
                 */
                String message = ex.getMessage() == null
                        ? ex.getClass().getSimpleName()
                        : ex.getMessage();

                /*
                 * Jeżeli po tej próbie osiągamy limit maxAttempts,
                 * event jest uznany za trwale nieopublikowany.
                 */
                if (event.getAttempts() + 1 >= properties.maxAttempts()) {
                    event.markFailed(message);

                    /*
                     * Kopia eventu trafia do DLQ.
                     * To pozwala później ręcznie zbadać payload i przyczynę błędu.
                     */
                    deadLetterRepository.save(
                            new DeadLetterEvent(event, message)
                    );
                } else {
                    /*
                     * Event zostaje ustawiony do ponowienia.
                     * retryLater powinno zwiększyć attempts i zapisać ostatni błąd.
                     *
                     * W pełniejszym modelu można tu też ustawić nextAttemptAt
                     * z exponential backoff.
                     */
                    event.retryLater(message);
                }
            }
        }
    }
}
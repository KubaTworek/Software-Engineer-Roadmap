package com.example.notification.application;

import com.example.notification.domain.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Komponent publikujący zdarzenia z outboxa.
 *
 * To jest implementacja Outbox Pattern.
 *
 * NotificationService nie wrzuca jobów bezpośrednio do kolejki.
 * Zamiast tego zapisuje:
 * - Notification,
 * - OutboxEvent.
 *
 * Dopiero OutboxPublisher cyklicznie czyta pending eventy z outboxa
 * i zamienia je na konkretne akcje systemowe.
 *
 * Główne zadania:
 * - pobrać nieopublikowane OutboxEvent,
 * - obsłużyć event NOTIFICATION_CREATED,
 * - obsłużyć event NOTIFICATION_CANCELLED,
 * - utworzyć NotificationJob per kanał,
 * - wrzucić joby do kolejki,
 * - oznaczyć event jako PUBLISHED,
 * - zapisać audyt i metryki.
 *
 * W prawdziwej produkcji outbox powinien być tabelą w tej samej bazie
 * i tej samej transakcji co zapis Notification.
 */
@Component
public class OutboxPublisher {
    private final Ports.OutboxRepository outboxRepository;
    private final Ports.NotificationRepository notificationRepository;
    private final Ports.NotificationJobRepository jobRepository;
    private final Ports.NotificationQueue queue;
    private final AuditService auditService;

    /**
     * Maksymalna liczba prób wysłania joba.
     *
     * Ta wartość trafia do każdego tworzonego NotificationJob.
     * Później NotificationWorker używa jej przy retry.
     */
    private final int maxAttempts;

    /**
     * Metryka liczby poprawnie opublikowanych eventów outboxa.
     *
     * Pomaga monitorować, czy publisher działa.
     * Jeśli Notification są tworzone, ale ten licznik nie rośnie,
     * to znaczy, że pipeline zatrzymał się na outboxie.
     */
    private final Counter publishedCounter;

    public OutboxPublisher(
            Ports.OutboxRepository outboxRepository,
            Ports.NotificationRepository notificationRepository,
            Ports.NotificationJobRepository jobRepository,
            Ports.NotificationQueue queue,
            AuditService auditService,
            MeterRegistry meterRegistry,
            @Value("${notification.retry.max-attempts:3}") int maxAttempts
    ) {
        this.outboxRepository = outboxRepository;
        this.notificationRepository = notificationRepository;
        this.jobRepository = jobRepository;
        this.queue = queue;
        this.auditService = auditService;
        this.maxAttempts = maxAttempts;

        /*
         * Licznik opublikowanych eventów outboxa.
         * Dostępny później przez Actuator/Prometheus.
         */
        this.publishedCounter = Counter
                .builder("notification_outbox_published_total")
                .register(meterRegistry);
    }

    /**
     * Cyklicznie publikuje pending eventy z outboxa.
     *
     * Scheduler uruchamia tę metodę co:
     *
     * notification.outbox.fixed-delay-ms
     *
     * Domyślnie: co 700 ms.
     *
     * W każdym przebiegu pobieramy maksymalnie 25 pending eventów.
     * Limit chroni aplikację przed zbyt długim pojedynczym przebiegiem schedulera.
     */
    @Scheduled(fixedDelayString = "${notification.outbox.fixed-delay-ms:700}")
    public void publishPending() {
        /*
         * Pobieramy paczkę eventów oczekujących na publikację.
         *
         * W prawdziwej produkcji to powinno być zrobione atomowo,
         * np. SELECT ... FOR UPDATE SKIP LOCKED,
         * żeby wiele instancji publishera nie przetwarzało tego samego eventu.
         */
        for (OutboxEvent event : outboxRepository.findPending(25)) {
            try {
                /*
                 * Event utworzenia Notification.
                 *
                 * Ten event oznacza:
                 * "Notification zostało zapisane, teraz można stworzyć joby per kanał".
                 */
                if ("NOTIFICATION_CREATED".equals(event.getEventType())) {
                    publishCreated(event);
                }

                /*
                 * Event anulowania Notification.
                 *
                 * Ten event oznacza:
                 * "Notification zostało anulowane, trzeba anulować nieprzetworzone joby".
                 */
                if ("NOTIFICATION_CANCELLED".equals(event.getEventType())) {
                    publishCancelled(event);
                }

                /*
                 * Jeśli obsługa eventu się udała, oznaczamy event jako PUBLISHED.
                 *
                 * Dzięki temu publisher nie będzie go przetwarzał ponownie.
                 */
                event.markPublished();
                outboxRepository.save(event);

                /*
                 * Audytujemy publikację outboxa.
                 *
                 * To pomaga śledzić, czy event przeszedł z etapu "zapisany"
                 * do etapu "opublikowany/przetworzony".
                 */
                auditService.record(
                        event.getTenantId(),
                        "system",
                        AuditAction.OUTBOX_PUBLISHED,
                        event.getAggregateId(),
                        Map.of("type", event.getEventType())
                );

                /*
                 * Metryka poprawnie opublikowanego outbox eventu.
                 */
                publishedCounter.increment();

            } catch (RuntimeException ex) {
                /*
                 * Jeśli publikacja eventu się nie udała, nie kasujemy go.
                 * Oznaczamy go jako FAILED i zapisujemy błąd.
                 *
                 * W pełniejszej produkcyjnej wersji warto dodać:
                 * - retry outbox eventów,
                 * - licznik prób,
                 * - alerty,
                 * - osobny status DEAD,
                 * - możliwość ręcznego replay.
                 */
                event.markFailed(ex.getMessage());
                outboxRepository.save(event);
            }
        }
    }

    /**
     * Obsługuje event NOTIFICATION_CREATED.
     *
     * Ta metoda zamienia jedno Notification na wiele NotificationJobów.
     *
     * Przykład:
     * Notification ma selectedChannels:
     * - EMAIL,
     * - PUSH,
     * - IN_APP.
     *
     * Publisher utworzy trzy joby:
     * - EMAIL job,
     * - PUSH job,
     * - IN_APP job.
     */
    private void publishCreated(OutboxEvent event) {
        /*
         * Pobieramy Notification powiązane z eventem.
         *
         * event.aggregateId to ID Notification.
         * tenantId pilnuje izolacji danych między tenantami.
         */
        Notification n = notificationRepository
                .findById(event.getTenantId(), event.getAggregateId())
                .orElseThrow(() -> new IllegalStateException(
                        "Notification not found"
                ));

        /*
         * Jeśli Notification wygasło zanim outbox zdążył je opublikować,
         * nie tworzymy jobów.
         *
         * Przykład:
         * - OTP,
         * - reset hasła,
         * - krótki alert.
         *
         * Nie ma sensu wysyłać przeterminowanej wiadomości.
         */
        if (n.isExpired()) {
            n.markExpired();
            notificationRepository.save(n);
            return;
        }

        /*
         * Jeśli Notification zostało anulowane, nic nie publikujemy.
         *
         * Może się zdarzyć, że użytkownik/admin anulował Notification,
         * zanim OutboxPublisher zdążył przetworzyć event CREATED.
         */
        if (n.getStatus() == NotificationStatus.CANCELLED) {
            return;
        }

        /*
         * Dla każdego wybranego kanału tworzymy osobny NotificationJob.
         *
         * To jest kluczowy moment rozbicia Notification na kanały.
         *
         * Dlaczego osobne joby?
         * - każdy kanał może mieć innego providera,
         * - każdy kanał może mieć inny status,
         * - retry może działać niezależnie,
         * - EMAIL może się udać, a PUSH może failować,
         * - łatwiej monitorować i debugować.
         */
        for (Channel channel : n.getSelectedChannels()) {
            NotificationJob job = new NotificationJob(
                    UUID.randomUUID(),
                    n.getId(),
                    n.getTenantId(),
                    n.getUserId(),
                    n.getNotificationType(),
                    channel,
                    n.getContactPoint(),
                    n.getPayload(),
                    maxAttempts,
                    n.getExpiresAt()
            );

            /*
             * Zapisujemy job.
             *
             * Worker później pobierze job z repozytorium po ID.
             */
            jobRepository.save(job);

            /*
             * Wrzucamy ID joba do kolejki.
             *
             * Kolejka przechowuje tylko ID,
             * a pełne dane joba są w jobRepository.
             */
            queue.enqueue(job.getId());

            /*
             * Audytujemy zakolejkowanie joba.
             *
             * Dzięki temu widać, że Notification zostało rozbite
             * na konkretny job kanałowy.
             */
            auditService.record(
                    n.getTenantId(),
                    "system",
                    AuditAction.JOB_QUEUED,
                    job.getId(),
                    Map.of("channel", channel.name())
            );
        }

        /*
         * Po utworzeniu jobów oznaczamy główne Notification jako QUEUED.
         *
         * To znaczy:
         * - Notification zostało przyjęte,
         * - joby zostały utworzone,
         * - system będzie próbował je przetworzyć asynchronicznie.
         */
        n.markQueued();
        notificationRepository.save(n);
    }

    /**
     * Obsługuje event NOTIFICATION_CANCELLED.
     *
     * Celem jest anulowanie jobów, które jeszcze nie zostały przetworzone.
     */
    private void publishCancelled(OutboxEvent event) {
        /*
         * Pobieramy wszystkie joby powiązane z anulowanym Notification.
         */
        for (NotificationJob job : jobRepository.findByNotificationId(
                event.getAggregateId()
        )) {
            /*
             * Anulujemy tylko joby, które są jeszcze w QUEUED.
             *
             * Nie anulujemy jobów:
             * - PROCESSING, bo worker już je obsługuje,
             * - SENT, bo provider już przyjął wiadomość,
             * - DELIVERED, bo wiadomość dotarła,
             * - FAILED/BOUNCED, bo są już zakończone.
             */
            if (job.getStatus() == NotificationStatus.QUEUED) {
                job.markCancelled();
                jobRepository.save(job);
            }
        }
    }
}
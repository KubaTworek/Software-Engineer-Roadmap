package com.example.notification.application;

import com.example.notification.domain.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Serwis odpowiedzialny za obsługę webhooków od zewnętrznych providerów.
 *
 * Providerzy typu SendGrid, SES, Twilio, FCM, APNs często działają asynchronicznie.
 * To znaczy:
 *
 * 1. NotificationWorker wysyła wiadomość do providera.
 * 2. Provider zwraca providerMessageId.
 * 3. Job przechodzi na SENT.
 * 4. Po czasie provider wysyła webhook:
 *    - DELIVERED,
 *    - BOUNCED,
 *    - FAILED.
 *
 * Ten serwis aktualizuje status joba na podstawie takiego webhooka.
 *
 * Ważne:
 * SENT nie oznacza dostarczenia do użytkownika.
 * SENT oznacza tylko, że provider przyjął wiadomość.
 *
 * DELIVERED/BOUNCED/FAILED przychodzą właśnie przez webhook.
 */
@Service
public class ProviderWebhookService {
    private final Ports.NotificationJobRepository jobRepository;
    private final Ports.NotificationRepository notificationRepository;
    private final AuditService auditService;

    /**
     * Metryka liczby jobów potwierdzonych jako dostarczone.
     *
     * Rośnie po webhooku DELIVERED.
     */
    private final Counter deliveredCounter;

    /**
     * Metryka liczby jobów odbitych przez providera.
     *
     * Rośnie po webhooku BOUNCED.
     *
     * Dla emaili bounce może oznaczać np. nieistniejącą skrzynkę.
     * Dla SMS może oznaczać problem z numerem albo operatorem.
     */
    private final Counter bouncedCounter;

    public ProviderWebhookService(
            Ports.NotificationJobRepository jobRepository,
            Ports.NotificationRepository notificationRepository,
            AuditService auditService,
            MeterRegistry meterRegistry
    ) {
        this.jobRepository = jobRepository;
        this.notificationRepository = notificationRepository;
        this.auditService = auditService;

        /*
         * Licznik dostarczonych jobów.
         * Dostępny później np. w /actuator/prometheus.
         */
        this.deliveredCounter = Counter
                .builder("notification_jobs_delivered_total")
                .register(meterRegistry);

        /*
         * Licznik odbitych jobów.
         * Przydatny do monitorowania deliverability.
         */
        this.bouncedCounter = Counter
                .builder("notification_jobs_bounced_total")
                .register(meterRegistry);
    }

    /**
     * Obsługuje pojedynczy webhook od providera.
     *
     * providerMessageId jest kluczowy.
     * To po nim system znajduje konkretny NotificationJob.
     *
     * Przykład:
     * - worker wysłał email,
     * - SendGrid zwrócił providerMessageId = sg-123,
     * - później SendGrid wysyła webhook DELIVERED dla sg-123,
     * - ten serwis znajduje job po sg-123 i oznacza go jako DELIVERED.
     */
    public void handle(
            String providerMessageId,
            ProviderWebhookStatus status,
            String reason
    ) {
        /*
         * Szukamy joba po ID wiadomości nadanym przez providera.
         *
         * Jeśli nie znajdujemy joba, webhook jest nieznany.
         * Może to oznaczać:
         * - webhook przyszedł z błędnym ID,
         * - webhook dotyczy starej wiadomości,
         * - provider wysłał callback zanim zapisaliśmy providerMessageId,
         * - błąd integracji.
         *
         * W tej implementacji rzucamy wyjątek.
         * Produkcyjnie warto rozważyć tabelę pending_webhooks i późniejszy reconciliation.
         */
        NotificationJob job = jobRepository
                .findByProviderMessageId(providerMessageId)
                .orElseThrow(() -> new Exceptions.NotificationValidationException(
                        "Unknown providerMessageId: " + providerMessageId
                ));

        /*
         * Mapujemy status od providera na wewnętrzny status joba.
         *
         * ProviderWebhookStatus to uproszczony, ujednolicony status.
         * W prawdziwym systemie każdy provider ma własne eventy,
         * np. SendGrid: delivered, bounce, dropped, spamreport.
         * Adapter webhooka powinien je najpierw znormalizować.
         */
        switch (status) {
            case DELIVERED -> {
                /*
                 * Provider potwierdził dostarczenie wiadomości.
                 *
                 * Job przechodzi z SENT na DELIVERED.
                 */
                job.markDelivered();

                /*
                 * Audytujemy dostarczenie.
                 *
                 * Zapisujemy providerMessageId, żeby dało się powiązać
                 * event dostarczenia z zewnętrznym providerem.
                 */
                auditService.record(
                        job.getTenantId(),
                        "provider",
                        AuditAction.JOB_DELIVERED,
                        job.getId(),
                        Map.of("providerMessageId", providerMessageId)
                );

                /*
                 * Metryka dostarczeń.
                 */
                deliveredCounter.increment();
            }

            case BOUNCED -> {
                /*
                 * Provider zgłosił bounce.
                 *
                 * Dla emaila może to oznaczać:
                 * - mailbox unavailable,
                 * - invalid address,
                 * - domain rejected,
                 * - spam complaint zależnie od mapowania.
                 */
                job.markBounced(
                        reason == null
                                ? "Provider reported bounce"
                                : reason
                );

                /*
                 * Audytujemy bounce.
                 *
                 * W produkcji taki event może też aktualizować suppression list,
                 * np. żeby nie wysyłać więcej emaili na twardo odbity adres.
                 */
                auditService.record(
                        job.getTenantId(),
                        "provider",
                        AuditAction.JOB_BOUNCED,
                        job.getId(),
                        Map.of("providerMessageId", providerMessageId)
                );

                /*
                 * Metryka bounce.
                 */
                bouncedCounter.increment();
            }

            case FAILED -> {
                /*
                 * Provider zgłosił finalny błąd dostarczenia.
                 *
                 * To różni się od błędu wysyłki w workerze.
                 * Worker mógł poprawnie wysłać request do providera,
                 * ale provider później stwierdził, że wiadomości nie dostarczy.
                 */
                job.markFailed(
                        reason == null
                                ? "Provider reported failure"
                                : reason
                );

                /*
                 * Audytujemy finalny failure od providera.
                 */
                auditService.record(
                        job.getTenantId(),
                        "provider",
                        AuditAction.JOB_FAILED,
                        job.getId(),
                        Map.of("providerMessageId", providerMessageId)
                );
            }
        }

        /*
         * Zapisujemy zaktualizowany status joba.
         *
         * Od tego momentu GET /jobs pokaże np. DELIVERED albo BOUNCED.
         */
        jobRepository.save(job);

        /*
         * Po zmianie statusu joba trzeba przeliczyć status głównego Notification.
         *
         * Notification agreguje status wielu jobów/kanałów.
         */
        refreshNotification(job);
    }

    /**
     * Aktualizuje status głównego Notification na podstawie statusów jego jobów.
     *
     * Jeden Notification może mieć wiele NotificationJob:
     * - EMAIL,
     * - SMS,
     * - PUSH,
     * - IN_APP.
     *
     * Webhook dotyczy jednego joba, ale status nadrzędnego Notification
     * powinien odzwierciedlać całość.
     */
    private void refreshNotification(NotificationJob job) {
        /*
         * Pobieramy wszystkie joby powiązane z tym samym Notification.
         */
        var jobs = jobRepository.findByNotificationId(
                job.getNotificationId()
        );

        /*
         * Pobieramy Notification w ramach tenanta joba.
         *
         * TenantId jest ważny, żeby nie aktualizować przypadkowo danych innego tenanta.
         */
        notificationRepository
                .findById(job.getTenantId(), job.getNotificationId())
                .ifPresent(n -> {

                    /*
                     * Jeśli wszystkie joby są DELIVERED,
                     * całe Notification uznajemy za DELIVERED.
                     *
                     * Przykład:
                     * EMAIL delivered,
                     * PUSH delivered,
                     * IN_APP delivered.
                     */
                    if (!jobs.isEmpty()
                            && jobs.stream().allMatch(j ->
                            j.getStatus() == NotificationStatus.DELIVERED
                    )) {
                        n.markDelivered();

                        /*
                         * Jeśli jakikolwiek job ma BOUNCED,
                         * całe Notification oznaczamy jako BOUNCED.
                         *
                         * To jest uproszczona polityka agregacji.
                         * W większym systemie warto dodać status PARTIALLY_DELIVERED
                         * albo PARTIALLY_FAILED.
                         */
                    } else if (jobs.stream().anyMatch(j ->
                            j.getStatus() == NotificationStatus.BOUNCED
                    )) {
                        n.markBounced();

                        /*
                         * Jeśli jakikolwiek job ma FAILED,
                         * całe Notification oznaczamy jako FAILED.
                         *
                         * To również uproszczenie.
                         * Przy wielu kanałach jeden FAILED nie musi oznaczać,
                         * że cała komunikacja do użytkownika się nie udała.
                         */
                    } else if (jobs.stream().anyMatch(j ->
                            j.getStatus() == NotificationStatus.FAILED
                    )) {
                        n.markFailed();
                    }

                    /*
                     * Zapisujemy zaktualizowany status Notification.
                     */
                    notificationRepository.save(n);
                });
    }
}
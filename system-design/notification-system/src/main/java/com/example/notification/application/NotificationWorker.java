package com.example.notification.application;

import com.example.notification.domain.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Worker odpowiedzialny za faktyczne przetwarzanie NotificationJobów.
 *
 * To jest komponent wykonawczy systemu.
 *
 * NotificationService tworzy tylko intencję wysyłki i zapisuje OutboxEvent.
 * OutboxPublisher tworzy joby i wrzuca je do kolejki.
 * Dopiero NotificationWorker pobiera job z kolejki i próbuje realnie wysłać wiadomość.
 *
 * Główne odpowiedzialności tej klasy:
 * - pobranie gotowego joba z kolejki,
 * - oznaczenie joba jako PROCESSING,
 * - pobranie template’u,
 * - wyrenderowanie wiadomości,
 * - wysłanie przez providera,
 * - użycie fallback providera, jeśli primary zawiedzie,
 * - retry dla błędów tymczasowych,
 * - przeniesienie do DLQ po przekroczeniu limitu prób,
 * - aktualizacja statusu głównego Notification,
 * - audyt i metryki.
 */
@Component
public class NotificationWorker {
    private final Ports.NotificationQueue queue;
    private final Ports.NotificationJobRepository jobRepository;
    private final Ports.NotificationRepository notificationRepository;
    private final Ports.TemplateRepository templateRepository;
    private final Ports.TemplateRenderer templateRenderer;
    private final ProviderRegistry providerRegistry;
    private final AuditService auditService;

    /**
     * Bazowe opóźnienie retry.
     *
     * Używane do exponential backoff:
     * - 1 próba retry: baseDelayMs,
     * - 2 próba retry: baseDelayMs * 2,
     * - 3 próba retry: baseDelayMs * 4.
     */
    private final long baseDelayMs;

    /**
     * Metryka liczby jobów poprawnie wysłanych do providera.
     *
     * Uwaga: SENT oznacza, że provider przyjął wiadomość.
     * Nie oznacza jeszcze DELIVERED.
     * DELIVERED przychodzi później przez webhook providera.
     */
    private final Counter sentCounter;

    /**
     * Metryka liczby jobów zakończonych błędem.
     *
     * Rośnie wtedy, gdy job trafia do DLQ albo ma błąd permanentny.
     */
    private final Counter failedCounter;

    public NotificationWorker(
            Ports.NotificationQueue queue,
            Ports.NotificationJobRepository jobRepository,
            Ports.NotificationRepository notificationRepository,
            Ports.TemplateRepository templateRepository,
            Ports.TemplateRenderer templateRenderer,
            ProviderRegistry providerRegistry,
            AuditService auditService,
            MeterRegistry meterRegistry,
            @Value("${notification.retry.base-delay-ms:1500}") long baseDelayMs
    ) {
        this.queue = queue;
        this.jobRepository = jobRepository;
        this.notificationRepository = notificationRepository;
        this.templateRepository = templateRepository;
        this.templateRenderer = templateRenderer;
        this.providerRegistry = providerRegistry;
        this.auditService = auditService;
        this.baseDelayMs = baseDelayMs;

        /*
         * Licznik jobów wysłanych do providera.
         * W produkcji trafia do /actuator/prometheus.
         */
        this.sentCounter = Counter.builder("notification_jobs_sent_total")
                .register(meterRegistry);

        /*
         * Licznik jobów zakończonych błędem.
         * Przydatny do alertów, np. gdy failure rate rośnie.
         */
        this.failedCounter = Counter.builder("notification_jobs_failed_total")
                .register(meterRegistry);
    }

    /**
     * Scheduler cyklicznie odpala workera.
     *
     * fixedDelay oznacza:
     * - poczekaj aż metoda się zakończy,
     * - odczekaj określony czas,
     * - uruchom ponownie.
     *
     * Domyślnie worker co 1000 ms próbuje pobrać jeden gotowy job z kolejki.
     *
     * W produkcji można mieć wiele instancji workerów,
     * ale wtedy kolejka i lockowanie jobów muszą być rozproszone,
     * np. Redis/Kafka/SQS/PostgreSQL SKIP LOCKED.
     */
    @Scheduled(fixedDelayString = "${notification.worker.fixed-delay-ms:1000}")
    public void processNextJob() {
        /*
         * queue.pollReadyJob() zwraca tylko job gotowy do przetworzenia.
         *
         * Job może nie być gotowy, jeśli jest zaplanowany jako retry
         * i jego nextAttemptAt jest jeszcze w przyszłości.
         */
        queue.pollReadyJob().ifPresent(this::processJob);
    }

    /**
     * Przetwarza pojedynczy NotificationJob.
     *
     * To jest główny flow wysyłki jednego kanału.
     *
     * Jeden Notification może mieć wiele jobów, np.:
     * - EMAIL,
     * - SMS,
     * - PUSH,
     * - IN_APP.
     *
     * Ta metoda obsługuje dokładnie jeden job, czyli jeden kanał.
     */
    public void processJob(UUID jobId) {
        /*
         * Pobieramy job z repozytorium.
         *
         * Jeśli job nie istnieje, to jest błąd spójności między kolejką a repozytorium.
         * W prawdziwej produkcji warto byłoby to zalogować jako critical/infrastructure error.
         */
        NotificationJob job = jobRepository
                .findById(jobId)
                .orElseThrow(() -> new IllegalStateException(
                        "Job not found: " + jobId
                ));

        /*
         * Jeśli job został anulowany, worker nic z nim nie robi.
         *
         * To może się zdarzyć, gdy użytkownik/admin anulował Notification,
         * a OutboxPublisher oznaczył jeszcze nieprzetworzone joby jako CANCELLED.
         */
        if (job.getStatus() == NotificationStatus.CANCELLED) {
            return;
        }

        /*
         * Obsługa wygaśnięcia joba.
         *
         * Jeśli Notification miało expiresAt i czas minął,
         * nie próbujemy wysyłać starego powiadomienia.
         *
         * Przykład:
         * - kod OTP,
         * - reset hasła,
         * - krótkotrwały alert.
         */
        if (job.isExpired()) {
            job.markExpired();
            jobRepository.save(job);

            /*
             * Po zmianie statusu joba trzeba przeliczyć status głównego Notification.
             */
            refreshNotificationStatus(job);
            return;
        }

        try {
            /*
             * Oznaczamy job jako PROCESSING.
             *
             * Od tego momentu worker deklaruje:
             * "ten job jest aktualnie obsługiwany".
             */
            job.markProcessing();
            jobRepository.save(job);

            /*
             * Agregujący Notification też oznaczamy jako PROCESSING.
             *
             * Notification to obiekt nadrzędny.
             * Job to konkretna próba wysyłki dla jednego kanału.
             */
            notificationRepository
                    .findById(job.getTenantId(), job.getNotificationId())
                    .ifPresent(n -> {
                        n.markProcessing();
                        notificationRepository.save(n);
                    });

            /*
             * Pobieramy aktywny template dla:
             * - tenanta,
             * - typu powiadomienia,
             * - kanału.
             *
             * Template jest kanałowy, bo EMAIL, SMS i PUSH zwykle mają inną treść.
             */
            NotificationTemplate template = templateRepository
                    .findActiveByTypeAndChannel(
                            job.getTenantId(),
                            job.getNotificationType(),
                            job.getChannel()
                    )
                    .orElseThrow(() -> new Exceptions.NotificationValidationException(
                            "Template not found"
                    ));

            /*
             * Renderujemy wiadomość.
             *
             * TemplateRenderer zamienia placeholdery typu {{firstName}}
             * na wartości z payloadu joba.
             */
            RenderedNotification rendered = templateRenderer.render(
                    template,
                    job.getPayload()
            );

            /*
             * Wysyłamy wiadomość.
             *
             * sendWithFallback próbuje kolejnych providerów dla danego kanału.
             * Np. EMAIL:
             * - najpierw SendGrid,
             * - potem SES fallback.
             */
            ProviderSendResult result = sendWithFallback(job, rendered);

            /*
             * Sprawdzamy, czy użyto providera zapasowego.
             *
             * Pierwszy provider z registry traktowany jest jako primary.
             */
            String primary = providerRegistry
                    .providersFor(job.getChannel())
                    .get(0)
                    .providerName();

            boolean fallbackUsed = !primary.equals(result.providerName());

            /*
             * Oznaczamy job jako SENT.
             *
             * SENT oznacza:
             * - provider przyjął wiadomość,
             * - mamy providerMessageId,
             * - możemy później dopasować webhook dostarczenia.
             *
             * SENT nie oznacza jeszcze, że użytkownik dostał wiadomość.
             */
            job.markSent(result, fallbackUsed);
            jobRepository.save(job);

            /*
             * Aktualizujemy status głównego Notification na podstawie statusów jobów.
             */
            refreshNotificationStatus(job);

            /*
             * Audytujemy wysyłkę joba.
             *
             * Zapisujemy:
             * - providera,
             * - informację, czy użyto fallbacku.
             */
            auditService.record(
                    job.getTenantId(),
                    "system",
                    AuditAction.JOB_SENT,
                    job.getId(),
                    Map.of(
                            "provider", result.providerName(),
                            "fallbackUsed", fallbackUsed
                    )
            );

            /*
             * Metryka poprawnych wysyłek do providera.
             */
            sentCounter.increment();

        } catch (Exceptions.PermanentProviderException
                 | Exceptions.NotificationValidationException ex) {

            /*
             * Błędy permanentne nie powinny być retryowane.
             *
             * Przykłady:
             * - niepoprawny numer telefonu,
             * - niepoprawny email,
             * - brak template’u,
             * - payload nie nadaje się do renderowania.
             *
             * Taki job od razu trafia do fail/DLQ.
             */
            fail(job, ex.getMessage());

        } catch (RuntimeException ex) {

            /*
             * Pozostałe błędy traktujemy jako potencjalnie tymczasowe.
             *
             * Przykłady:
             * - timeout providera,
             * - 5xx providera,
             * - chwilowy problem sieciowy,
             * - chwilowy błąd infrastruktury.
             *
             * Dla nich próbujemy retry, jeśli limit prób nie został przekroczony.
             */
            retryOrFail(job, ex.getMessage());
        }
    }

    /**
     * Próbuje wysłać wiadomość przez providerów danego kanału.
     *
     * ProviderRegistry zwraca providerów w kolejności priorytetu:
     * - primary,
     * - fallback 1,
     * - fallback 2 itd.
     */
    private ProviderSendResult sendWithFallback(
            NotificationJob job,
            RenderedNotification rendered
    ) {
        RuntimeException last = null;

        /*
         * Wyciągamy adres odbiorcy właściwy dla kanału.
         *
         * EMAIL -> email
         * SMS -> phoneNumber
         * PUSH -> pushToken
         * IN_APP -> identyfikator użytkownika / lokalny kanał
         */
        String recipient = job
                .getContactPoint()
                .valueFor(job.getChannel());

        /*
         * Próbujemy wysłać wiadomość przez kolejnych providerów.
         */
        for (Ports.NotificationProvider provider
                : providerRegistry.providersFor(job.getChannel())) {

            try {
                return provider.send(
                        job.getTenantId(),
                        recipient,
                        rendered
                );

            } catch (Exceptions.PermanentProviderException ex) {
                /*
                 * Błąd permanentny przerywa fallback.
                 *
                 * Jeśli email jest niepoprawny, przełączenie z SendGrid na SES
                 * nie ma sensu — drugi provider też nie naprawi adresu.
                 */
                throw ex;

            } catch (RuntimeException ex) {
                /*
                 * Błąd tymczasowy pozwala spróbować kolejnego providera.
                 *
                 * Przykład:
                 * SendGrid ma timeout, więc próbujemy SES.
                 */
                last = ex;
            }
        }

        /*
         * Jeśli żaden provider nie zadziałał, rzucamy ostatni błąd.
         * Jeśli z jakiegoś powodu nie było błędu, tworzymy ogólny TransientProviderException.
         */
        throw last == null
                ? new Exceptions.TransientProviderException("All providers failed")
                : last;
    }

    /**
     * Obsługuje błąd potencjalnie tymczasowy.
     *
     * Jeśli job ma jeszcze dostępne próby, planujemy retry.
     * Jeśli nie, oznaczamy job jako failed i przenosimy go do DLQ.
     */
    private void retryOrFail(NotificationJob job, String error) {
        if (job.canRetry()) {
            /*
             * Wyliczamy czas kolejnej próby.
             *
             * attemptCount + 1, bo planujemy następną próbę,
             * a nie opisujemy poprzednią.
             */
            Instant next = Instant.now().plus(
                    backoff(job.getAttemptCount() + 1)
            );

            /*
             * scheduleRetry:
             * - ustawia status QUEUED,
             * - zwiększa attemptCount,
             * - zapisuje nextAttemptAt,
             * - zapisuje ostatni błąd.
             */
            job.scheduleRetry(next, error);
            jobRepository.save(job);

            /*
             * Wrzucamy job z powrotem do kolejki.
             *
             * Sama kolejka nie powinna go przetworzyć przed nextAttemptAt.
             */
            queue.enqueue(job.getId());

            /*
             * Audytujemy retry.
             *
             * To jest ważne operacyjnie:
             * pozwala sprawdzić, czy system walczy z problemem providera.
             */
            auditService.record(
                    job.getTenantId(),
                    "system",
                    AuditAction.JOB_RETRY_SCHEDULED,
                    job.getId(),
                    Map.of(
                            "attempt", job.getAttemptCount(),
                            "error", error
                    )
            );
        } else {
            /*
             * Limit prób został przekroczony.
             * Job trafia do DLQ.
             */
            fail(job, error);
        }
    }

    /**
     * Oznacza job jako FAILED i przenosi go do Dead Letter Queue.
     *
     * DLQ to miejsce na joby, których system nie potrafił dostarczyć automatycznie.
     * Operator może później sprawdzić DLQ i zdecydować, czy robić replay,
     * poprawić dane, czy zignorować problem.
     */
    private void fail(NotificationJob job, String error) {
        job.markFailed(error);
        jobRepository.save(job);

        /*
         * Przeniesienie do DLQ.
         *
         * W tej wersji DLQ jest w repozytorium in-memory.
         * W produkcji byłaby to osobna kolejka albo tabela.
         */
        jobRepository.moveToDeadLetter(job);

        /*
         * Po failu jednego joba trzeba przeliczyć status Notification.
         * Jeśli np. EMAIL failed, całe Notification może przejść na FAILED,
         * nawet jeśli inne kanały jeszcze są w toku.
         */
        refreshNotificationStatus(job);

        /*
         * Audytujemy finalny błąd.
         */
        auditService.record(
                job.getTenantId(),
                "system",
                AuditAction.JOB_FAILED,
                job.getId(),
                Map.of("error", error)
        );

        /*
         * Metryka błędów.
         */
        failedCounter.increment();
    }

    /**
     * Liczy exponential backoff.
     *
     * Wzór:
     * delay = baseDelayMs * 2^(attempt - 1)
     *
     * Przykład dla baseDelayMs = 1500:
     * - attempt 1 -> 1500 ms,
     * - attempt 2 -> 3000 ms,
     * - attempt 3 -> 6000 ms.
     */
    private Duration backoff(int attempt) {
        return Duration.ofMillis(
                baseDelayMs * (long) Math.pow(
                        2,
                        Math.max(0, attempt - 1)
                )
        );
    }

    /**
     * Aktualizuje status głównego Notification na podstawie statusów jobów.
     *
     * Notification jest agregatem nadrzędnym.
     * NotificationJob reprezentuje konkretny kanał.
     *
     * Przykład:
     * Notification PAYMENT_FAILED może mieć joby:
     * - EMAIL: SENT,
     * - PUSH: SENT,
     * - IN_APP: SENT.
     *
     * Wtedy Notification może przejść na SENT.
     */
    private void refreshNotificationStatus(NotificationJob job) {
        /*
         * Pobieramy wszystkie joby powiązane z tym samym Notification.
         */
        var jobs = jobRepository.findByNotificationId(
                job.getNotificationId()
        );

        /*
         * Pobieramy główne Notification w ramach tenanta joba.
         */
        notificationRepository
                .findById(job.getTenantId(), job.getNotificationId())
                .ifPresent(n -> {

                    /*
                     * Jeśli Notification wygasło, status nadrzędny to EXPIRED.
                     */
                    if (n.isExpired()) {
                        n.markExpired();

                        /*
                         * Jeśli wszystkie joby są DELIVERED,
                         * całe Notification jest DELIVERED.
                         *
                         * Ten status zwykle pochodzi z webhooków providerów.
                         */
                    } else if (!jobs.isEmpty()
                            && jobs.stream().allMatch(j ->
                            j.getStatus() == NotificationStatus.DELIVERED
                    )) {
                        n.markDelivered();

                        /*
                         * Jeśli wszystkie joby są SENT albo DELIVERED,
                         * Notification jest SENT.
                         *
                         * To znaczy: system przekazał wiadomości providerom,
                         * ale niekoniecznie ma jeszcze potwierdzenie dostarczenia.
                         */
                    } else if (!jobs.isEmpty()
                            && jobs.stream().allMatch(j ->
                            j.getStatus() == NotificationStatus.SENT
                                    || j.getStatus() == NotificationStatus.DELIVERED
                    )) {
                        n.markSent();

                        /*
                         * Jeśli którykolwiek job jest FAILED albo BOUNCED,
                         * całe Notification traktujemy jako FAILED.
                         *
                         * To jest uproszczona polityka.
                         * W bardziej zaawansowanej wersji można mieć status PARTIAL_FAILED.
                         */
                    } else if (jobs.stream().anyMatch(j ->
                            j.getStatus() == NotificationStatus.FAILED
                                    || j.getStatus() == NotificationStatus.BOUNCED
                    )) {
                        n.markFailed();

                        /*
                         * Jeśli przynajmniej jeden job jest PROCESSING,
                         * Notification też jest PROCESSING.
                         */
                    } else if (jobs.stream().anyMatch(j ->
                            j.getStatus() == NotificationStatus.PROCESSING
                    )) {
                        n.markProcessing();
                    }

                    /*
                     * Zapisujemy zaktualizowany status agregatu.
                     */
                    notificationRepository.save(n);
                });
    }
}
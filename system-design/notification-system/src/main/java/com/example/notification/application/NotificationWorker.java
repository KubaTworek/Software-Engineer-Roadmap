package com.example.notification.application;

import com.example.notification.domain.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
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
    private final ProviderDeliveryExecutor deliveryExecutor;
    private final Ports.PreferenceService preferenceService;
    private final AuditService auditService;
    private final NotificationStatusPolicy statusPolicy;
    private final SuppressionService suppressionService;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

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
    private final Counter unknownCounter;
    private final Timer timeToSend;

    public NotificationWorker(
            Ports.NotificationQueue queue,
            Ports.NotificationJobRepository jobRepository,
            Ports.NotificationRepository notificationRepository,
            Ports.TemplateRepository templateRepository,
            Ports.TemplateRenderer templateRenderer,
            ProviderDeliveryExecutor deliveryExecutor,
            Ports.PreferenceService preferenceService,
            AuditService auditService,
            NotificationStatusPolicy statusPolicy,
            SuppressionService suppressionService,
            MeterRegistry meterRegistry,
            Clock clock,
            @Value("${notification.retry.base-delay-ms:1500}") long baseDelayMs
    ) {
        this.queue = queue;
        this.jobRepository = jobRepository;
        this.notificationRepository = notificationRepository;
        this.templateRepository = templateRepository;
        this.templateRenderer = templateRenderer;
        this.deliveryExecutor = deliveryExecutor;
        this.preferenceService = preferenceService;
        this.auditService = auditService;
        this.statusPolicy = statusPolicy;
        this.suppressionService = suppressionService;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
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
        this.unknownCounter = Counter.builder("notification_provider_unknown_total")
                .description("Provider calls whose external effect is ambiguous")
                .register(meterRegistry);
        this.timeToSend = Timer.builder("notification_time_to_send_seconds")
                .description("Time from durable acceptance to provider acceptance")
                .publishPercentileHistogram()
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
         * PROCESSING z poprzedniego lease'u oznacza crash window. Nie wiemy, czy
         * provider wykonał efekt, więc odzyskanie nie może ponownie wysłać wiadomości.
         */
        if (job.getStatus() == NotificationStatus.PROCESSING) {
            job.markUnknown("Recovered PROCESSING job after worker crash; reconcile provider operation");
            jobRepository.save(job);
            unknownCounter.increment();
            refreshNotificationStatus(job);
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
        if (job.isExpired(clock.instant())) {
            job.markExpired();
            jobRepository.save(job);

            /*
             * Po zmianie statusu joba trzeba przeliczyć status głównego Notification.
             */
            refreshNotificationStatus(job);
            return;
        }

        // Consent jest sprawdzany ponownie bezpośrednio przed efektem zewnętrznym.
        // Dzięki temu opt-out wykonany po zakolejkowaniu blokuje marketing, ale nie
        // obowiązkowe komunikaty bezpieczeństwa.
        if (!preferenceService.isAllowedNow(job.getTenantId(), job.getUserId(),
                job.getNotificationType(), job.getChannel())) {
            job.markCancelled();
            jobRepository.save(job);
            refreshNotificationStatus(job);
            return;
        }
        if (suppressionService.isSuppressed(job.getTenantId(), job.getChannel(),
                job.getContactPoint().valueFor(job.getChannel()))) {
            job.markCancelled();
            jobRepository.save(job);
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
                    .findById(job.getTenantId(), job.getTemplateId())
                    .orElseThrow(() -> new Exceptions.NotificationValidationException(
                            "Pinned template version not found"
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
            ProviderDeliveryExecutor.Result delivery = deliveryExecutor.send(job, rendered);
            ProviderSendResult result = delivery.providerResult();

            /*
             * Sprawdzamy, czy użyto providera zapasowego.
             *
             * Pierwszy provider z registry traktowany jest jako primary.
             */
            boolean fallbackUsed = delivery.fallbackUsed();

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
            timeToSend.record(Duration.between(job.getCreatedAt(), clock.instant()));

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

        } catch (PostProviderEffectProbe.SimulatedProcessCrash crash) {
            // Celowo nie zapisujemy żadnego rozstrzygnięcia: symulujemy utratę procesu.
            // Wygasły lease pozwoli innemu workerowi odzyskać PROCESSING jako UNKNOWN.
            throw crash;
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

        } catch (Exceptions.AdmissionRejectedException ex) {
            // Przeciążenie lokalne oznacza „spróbuj później”, a nie „uderz do
            // kolejnego providera”. Fallback w tej sytuacji tworzyłby retry storm.
            retryOrFail(job, ex.getMessage());
        } catch (Exceptions.ProviderCallException ex) {
            if (ex.certainty() == Exceptions.OutcomeCertainty.UNKNOWN) {
                // Po timeoutcie po wysłaniu requestu nie wolno automatycznie użyć
                // drugiego providera: pierwszy mógł już przyjąć wiadomość.
                job.markUnknown(ex.getMessage());
                jobRepository.save(job);
                unknownCounter.increment();
                refreshNotificationStatus(job);
            } else {
                retryOrFail(job, ex.getMessage());
            }
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
            Duration delay = backoff(job.getAttemptCount() + 1);
            long jitter = Math.floorMod(job.getId().hashCode(), Math.max(1, delay.toMillis() / 2));
            Instant next = clock.instant().plus(delay).plusMillis(jitter);

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
                    statusPolicy.apply(n, jobs);

                    /*
                     * Zapisujemy zaktualizowany status agregatu.
                     */
                    notificationRepository.save(n);
                });
    }

}

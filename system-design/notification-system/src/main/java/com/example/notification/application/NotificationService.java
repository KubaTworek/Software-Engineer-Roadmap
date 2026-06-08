package com.example.notification.application;

import com.example.notification.domain.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Główny serwis aplikacyjny odpowiedzialny za tworzenie i anulowanie powiadomień.
 *
 * To jest jeden z najważniejszych elementów systemu.
 * Kontroler REST tylko przyjmuje request, ale realna decyzja biznesowa dzieje się tutaj.
 *
 * NotificationService odpowiada za:
 * - rate limiting,
 * - idempotencję,
 * - deduplikację,
 * - wybór kanałów na podstawie preferencji użytkownika,
 * - walidację danych kontaktowych,
 * - walidację payloadu względem template’ów,
 * - zapis Notification,
 * - zapis OutboxEvent,
 * - audyt,
 * - metryki.
 *
 * Ważne: ten serwis NIE wysyła powiadomień bezpośrednio.
 * Po utworzeniu Notification zapisuje OutboxEvent, który później zostanie przetworzony
 * przez OutboxPublisher. To jest implementacja Outbox Pattern.
 */
@Service
public class NotificationService {
    private final Ports.NotificationRepository notificationRepository;
    private final Ports.OutboxRepository outboxRepository;
    private final Ports.TemplateRepository templateRepository;
    private final Ports.PreferenceService preferenceService;
    private final DeduplicationKeyFactory deduplicationKeyFactory;
    private final RateLimiterService rateLimiterService;
    private final AuditService auditService;

    /**
     * Okno czasowe deduplikacji.
     *
     * Jeśli identyczne powiadomienie pojawi się ponownie w tym oknie,
     * system zwróci istniejące Notification zamiast tworzyć nowe.
     */
    private final long deduplicationWindowMinutes;

    /**
     * Metryka Prometheus/Micrometer.
     *
     * Liczy, ile nowych powiadomień realnie utworzono.
     * Nie powinna rosnąć przy trafieniu w idempotency/dedupe,
     * bo wtedy nie tworzymy nowego powiadomienia.
     */
    private final Counter createdCounter;

    public NotificationService(
            Ports.NotificationRepository notificationRepository,
            Ports.OutboxRepository outboxRepository,
            Ports.TemplateRepository templateRepository,
            Ports.PreferenceService preferenceService,
            DeduplicationKeyFactory deduplicationKeyFactory,
            RateLimiterService rateLimiterService,
            AuditService auditService,
            MeterRegistry meterRegistry,
            @Value("${notification.deduplication.window-minutes:10}") long deduplicationWindowMinutes
    ) {
        this.notificationRepository = notificationRepository;
        this.outboxRepository = outboxRepository;
        this.templateRepository = templateRepository;
        this.preferenceService = preferenceService;
        this.deduplicationKeyFactory = deduplicationKeyFactory;
        this.rateLimiterService = rateLimiterService;
        this.auditService = auditService;
        this.deduplicationWindowMinutes = deduplicationWindowMinutes;

        /*
         * Rejestracja metryki liczby utworzonych powiadomień.
         * W produkcji ta metryka może być zbierana przez Prometheusa
         * i pokazywana w Grafanie.
         */
        this.createdCounter = Counter.builder("notifications_created_total")
                .register(meterRegistry);
    }

    /**
     * Tworzy nowe powiadomienie.
     *
     * To jest główny flow tworzenia Notification.
     *
     * Kolejność jest istotna:
     * 1. rate limit,
     * 2. idempotency check,
     * 3. wybór kanałów,
     * 4. walidacja,
     * 5. deduplikacja,
     * 6. zapis Notification,
     * 7. zapis OutboxEvent,
     * 8. audyt,
     * 9. metryka.
     */
    public CreateNotificationResult create(
            String tenantId,
            String actor,
            String userId,
            NotificationType notificationType,
            List<Channel> requestedChannels,
            ContactPoint contactPoint,
            Map<String, Object> payload,
            String idempotencyKey,
            Instant expiresAt
    ) {
        /*
         * 1. Rate limiting.
         *
         * Chroni system przed nadmierną liczbą requestów.
         * Limit jest sprawdzany na poziomie tenant + user.
         *
         * To jest celowo na początku, żeby nie wykonywać kosztownych operacji,
         * jeśli request i tak powinien zostać odrzucony.
         */
        rateLimiterService.check(tenantId, userId);

        /*
         * 2. Idempotency check.
         *
         * IdempotencyKey chroni przed duplikatami wynikającymi np. z retry klienta.
         *
         * Przykład:
         * PaymentService wysyła request, ale dostaje timeout.
         * Ponawia ten sam request z tym samym idempotencyKey.
         * System nie tworzy drugiego powiadomienia, tylko zwraca istniejące.
         */
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = notificationRepository.findByIdempotencyKey(
                    tenantId,
                    idempotencyKey
            );

            if (existing.isPresent()) {
                /*
                 * Audytujemy fakt, że request został rozpoznany jako duplikat.
                 * To jest przydatne przy debugowaniu integracji między serwisami.
                 */
                auditService.record(
                        tenantId,
                        actor,
                        AuditAction.NOTIFICATION_DUPLICATE_RETURNED,
                        existing.get().getId(),
                        Map.of("reason", "IDEMPOTENCY_KEY_MATCH")
                );

                /*
                 * Zwracamy istniejące Notification.
                 * duplicate=true mówi klientowi, że nic nowego nie zostało utworzone.
                 */
                return new CreateNotificationResult(
                        existing.get(),
                        true,
                        "IDEMPOTENCY_KEY_MATCH"
                );
            }
        }

        /*
         * 3. Normalizacja listy kanałów z requestu.
         *
         * Jeśli klient nie podał kanałów, przekazujemy pustą listę.
         * PreferenceService może wtedy dobrać kanały domyślne dla danego typu powiadomienia.
         */
        List<Channel> safeRequested = requestedChannels == null
                ? List.of()
                : requestedChannels;

        /*
         * 4. Wybór finalnych kanałów.
         *
         * PreferenceService bierze pod uwagę:
         * - tenantId,
         * - userId,
         * - typ powiadomienia,
         * - kanały z requestu,
         * - preferencje użytkownika,
         * - kanały domyślne.
         *
         * Wynik selected to kanały, dla których później zostaną utworzone joby.
         */
        List<Channel> selected = preferenceService.resolveChannels(
                tenantId,
                userId,
                notificationType,
                safeRequested
        );

        /*
         * Jeśli preferencje użytkownika odrzuciły wszystkie kanały,
         * nie ma czego wysyłać.
         *
         * To jest błąd walidacyjny na poziomie biznesowym.
         */
        if (selected.isEmpty()) {
            throw new Exceptions.NotificationValidationException(
                    "No channels selected after applying preferences"
            );
        }

        /*
         * 5. Walidacja danych kontaktowych.
         *
         * Dla każdego wybranego kanału musi istnieć odpowiedni contact point:
         * - EMAIL wymaga emaila,
         * - SMS wymaga numeru telefonu,
         * - PUSH wymaga push tokena,
         * - IN_APP nie wymaga zewnętrznego adresu.
         */
        validateContactPoints(selected, contactPoint);

        /*
         * 6. Walidacja template’ów i payloadu.
         *
         * Dla każdego wybranego kanału musi istnieć aktywny template.
         * Payload musi zawierać wszystkie zmienne wymagane przez template.
         *
         * To zabezpiecza przed wysłaniem wiadomości typu:
         * "Cześć {{firstName}}, kliknij {{resetLink}}"
         * z niewypełnionymi placeholderami.
         */
        validateTemplatesAndPayload(
                tenantId,
                notificationType,
                selected,
                payload
        );

        /*
         * 7. Deduplication key.
         *
         * IdempotencyKey działa tylko wtedy, gdy klient go poda.
         * DedupeKey jest tworzony przez system na podstawie treści requestu.
         *
         * Typowo zawiera:
         * - tenantId,
         * - userId,
         * - notificationType,
         * - kanały,
         * - payload.
         */
        String dedupeKey = deduplicationKeyFactory.create(
                tenantId,
                userId,
                notificationType,
                selected,
                payload
        );

        /*
         * 8. Deduplikacja w oknie czasowym.
         *
         * Jeśli takie samo powiadomienie już istnieje i mieści się w oknie dedupe,
         * zwracamy istniejący rekord zamiast tworzyć nowy.
         *
         * To chroni przed przypadkami, gdzie różne części systemu wygenerują
         * to samo powiadomienie niezależnie.
         */
        var duplicate = notificationRepository.findByDeduplicationKey(
                tenantId,
                dedupeKey
        );

        if (duplicate.isPresent()) {
            Notification existing = duplicate.get();

            boolean insideWindow = existing
                    .getCreatedAt()
                    .isAfter(Instant.now().minusSeconds(
                            deduplicationWindowMinutes * 60
                    ));

            /*
             * Nie traktujemy jako aktywnego duplikatu powiadomienia:
             * - wygasłego,
             * - anulowanego,
             * - spoza okna deduplikacji.
             */
            if (insideWindow
                    && !existing.isExpired()
                    && existing.getStatus() != NotificationStatus.CANCELLED) {

                auditService.record(
                        tenantId,
                        actor,
                        AuditAction.NOTIFICATION_DUPLICATE_RETURNED,
                        existing.getId(),
                        Map.of("reason", "DEDUPLICATION_WINDOW_MATCH")
                );

                return new CreateNotificationResult(
                        existing,
                        true,
                        "DEDUPLICATION_WINDOW_MATCH"
                );
            }
        }

        /*
         * 9. Utworzenie obiektu domenowego Notification.
         *
         * Na tym etapie powiadomienie jeszcze NIE jest wysłane.
         * To jest tylko zapis intencji wysyłki.
         *
         * Status początkowy ustawiany jest w domenie, zwykle CREATED.
         */
        Notification notification = new Notification(
                UUID.randomUUID(),
                tenantId,
                userId,
                notificationType,
                safeRequested,
                selected,
                contactPoint,
                payload,
                normalize(idempotencyKey),
                dedupeKey,
                expiresAt
        );

        /*
         * 10. Zapis Notification.
         *
         * W produkcji ten zapis powinien być częścią transakcji razem z OutboxEvent.
         * W tej wersji repozytoria są in-memory, ale wzorzec architektoniczny jest ten sam.
         */
        notificationRepository.save(notification);

        /*
         * 11. Outbox Pattern.
         *
         * Zamiast od razu tworzyć joby lub wysyłać wiadomości,
         * zapisujemy event NOTIFICATION_CREATED.
         *
         * Później OutboxPublisher:
         * - pobierze ten event,
         * - utworzy NotificationJob per kanał,
         * - wrzuci joby do kolejki.
         *
         * Dzięki temu API nie zależy bezpośrednio od workera ani providera.
         */
        outboxRepository.save(
                new OutboxEvent(
                        UUID.randomUUID(),
                        tenantId,
                        notification.getId(),
                        "NOTIFICATION_CREATED"
                )
        );

        /*
         * 12. Audyt.
         *
         * Zapisujemy fakt utworzenia powiadomienia.
         * W produkcji audyt pomaga ustalić:
         * - kto zainicjował wysyłkę,
         * - dla jakiego tenanta,
         * - jakiego typu było powiadomienie,
         * - jaki zasób został utworzony.
         */
        auditService.record(
                tenantId,
                actor,
                AuditAction.NOTIFICATION_CREATED,
                notification.getId(),
                Map.of("type", notificationType.name())
        );

        /*
         * 13. Metryka.
         *
         * Zwiększamy licznik realnie utworzonych powiadomień.
         * Nie zwiększamy go przy duplikatach.
         */
        createdCounter.increment();

        /*
         * Zwracamy wynik:
         * - notification,
         * - duplicate=false,
         * - duplicateReason=null.
         */
        return new CreateNotificationResult(
                notification,
                false,
                null
        );
    }

    /**
     * Anuluje powiadomienie.
     *
     * Anulowanie jest możliwe tylko wtedy, gdy domena Notification na to pozwala.
     * Zwykle można anulować powiadomienie w statusie CREATED albo QUEUED,
     * ale nie takie, które zostało już wysłane.
     */
    public Notification cancel(String tenantId, String actor, UUID notificationId) {
        /*
         * Szukamy powiadomienia w ramach konkretnego tenanta.
         * To zabezpiecza przed dostępem między tenantami.
         */
        Notification notification = notificationRepository
                .findById(tenantId, notificationId)
                .orElseThrow(() -> new Exceptions.NotificationValidationException(
                        "Notification not found: " + notificationId
                ));

        /*
         * Logika przejścia statusu jest w domenie.
         * Jeśli status nie pozwala na anulowanie, domena rzuci wyjątek.
         */
        notification.markCancelled();

        /*
         * Zapisujemy zaktualizowany status powiadomienia.
         */
        notificationRepository.save(notification);

        /*
         * Tworzymy event outbox.
         *
         * OutboxPublisher później obsłuży NOTIFICATION_CANCELLED,
         * np. oznaczając jeszcze nieprzetworzone joby jako CANCELLED.
         */
        outboxRepository.save(
                new OutboxEvent(
                        UUID.randomUUID(),
                        tenantId,
                        notification.getId(),
                        "NOTIFICATION_CANCELLED"
                )
        );

        /*
         * Audytujemy anulowanie.
         */
        auditService.record(
                tenantId,
                actor,
                AuditAction.NOTIFICATION_CANCELLED,
                notification.getId(),
                Map.of()
        );

        return notification;
    }

    /**
     * Sprawdza, czy dla każdego wybranego kanału istnieją dane kontaktowe.
     *
     * Przykład:
     * - jeśli selected zawiera EMAIL, contactPoint musi mieć email,
     * - jeśli selected zawiera SMS, contactPoint musi mieć phoneNumber,
     * - jeśli selected zawiera PUSH, contactPoint musi mieć pushToken.
     */
    private void validateContactPoints(
            List<Channel> channels,
            ContactPoint contactPoint
    ) {
        if (contactPoint == null) {
            throw new Exceptions.NotificationValidationException(
                    "contactPoint is required"
            );
        }

        for (Channel channel : channels) {
            if (!contactPoint.hasContactFor(channel)) {
                throw new Exceptions.NotificationValidationException(
                        "Missing contact point for channel: " + channel
                );
            }
        }
    }

    /**
     * Waliduje, czy istnieją aktywne template’y oraz czy payload zawiera wymagane zmienne.
     *
     * To jest krytyczne, bo notification worker później tylko renderuje template.
     * Jeśli tutaj przepuścimy zły payload, worker może wysłać błędną wiadomość
     * albo wiadomość z niewypełnionymi placeholderami.
     */
    private void validateTemplatesAndPayload(
            String tenantId,
            NotificationType type,
            List<Channel> channels,
            Map<String, Object> payload
    ) {
        if (payload == null) {
            throw new Exceptions.NotificationValidationException(
                    "payload is required"
            );
        }

        /*
         * Sprawdzamy każdy kanał osobno, bo EMAIL, SMS, PUSH i IN_APP
         * mogą mieć różne template’y i różne wymagane zmienne.
         */
        for (Channel channel : channels) {
            NotificationTemplate template = templateRepository
                    .findActiveByTypeAndChannel(tenantId, type, channel)
                    .orElseThrow(() -> new Exceptions.NotificationValidationException(
                            "Missing active template for type "
                                    + type
                                    + " and channel "
                                    + channel
                    ));

            /*
             * requiredVariables pochodzi z template’u.
             * Jeśli template wymaga np. firstName i resetLink,
             * payload musi zawierać oba pola.
             */
            for (String variable : template.getRequiredVariables()) {
                Object value = payload.get(variable);

                if (value == null || value.toString().isBlank()) {
                    throw new Exceptions.NotificationValidationException(
                            "Missing required payload variable '" + variable + "'"
                    );
                }
            }
        }
    }

    /**
     * Normalizuje puste stringi do null.
     *
     * Dzięki temu pusty idempotencyKey nie jest traktowany jak prawidłowy klucz.
     */
    private String normalize(String value) {
        return value == null || value.isBlank()
                ? null
                : value;
    }
}
package com.example.notification.api;

import com.example.notification.api.dto.ApiDtos;
import com.example.notification.application.*;
import com.example.notification.domain.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Zbiorczy kontener dla kontrolerów REST aplikacji Notification System.
 *
 * Ta klasa nie trzyma logiki biznesowej. Jej główna rola to:
 * - wystawienie endpointów HTTP,
 * - odczyt danych z requestu,
 * - pobranie tenantId z TenantContext,
 * - przekazanie operacji do serwisów aplikacyjnych,
 * - zwrócenie DTO jako odpowiedzi HTTP.
 *
 * W realnym projekcie każdy kontroler powinien być raczej osobnym plikiem.
 */
public final class Controllers {

    /**
     * Prywatny konstruktor blokuje tworzenie instancji klasy Controllers.
     * Klasa pełni wyłącznie funkcję kontenera na statyczne kontrolery.
     */
    private Controllers() {}

    /**
     * Główny kontroler do zarządzania pojedynczymi powiadomieniami.
     *
     * Obsługuje:
     * - tworzenie powiadomienia,
     * - listowanie powiadomień tenanta,
     * - pobranie szczegółów powiadomienia,
     * - pobranie jobów powiązanych z powiadomieniem,
     * - anulowanie powiadomienia.
     */
    @RestController
    @RequestMapping("/api/v1/notifications")
    public static class NotificationController {
        private final NotificationService notificationService;
        private final Ports.NotificationRepository notificationRepository;
        private final Ports.NotificationJobRepository jobRepository;

        public NotificationController(
                NotificationService notificationService,
                Ports.NotificationRepository notificationRepository,
                Ports.NotificationJobRepository jobRepository
        ) {
            this.notificationService = notificationService;
            this.notificationRepository = notificationRepository;
            this.jobRepository = jobRepository;
        }

        /**
         * Tworzy nowe powiadomienie.
         *
         * Ważne:
         * - endpoint zwraca HTTP 202 ACCEPTED, bo powiadomienie nie musi być wysłane natychmiast,
         * - request trafia do NotificationService,
         * - NotificationService obsługuje idempotencję, deduplikację, preferencje, rate limit i outbox,
         * - tenantId nie pochodzi z body, tylko z TenantContext, czyli z nagłówka X-Tenant-Id,
         * - actor pochodzi z nagłówka X-Actor i służy głównie do audytu.
         */
        @PostMapping
        @ResponseStatus(HttpStatus.ACCEPTED)
        public ApiDtos.CreateNotificationResponse create(
                @RequestHeader(value = "X-Actor", required = false) String actor,
                @Valid @RequestBody ApiDtos.CreateNotificationRequest request
        ) {
            ApiDtos.ContactPointRequest cp = request.contactPoint();

            /*
             * DTO z API zamieniamy na obiekt domenowy ContactPoint.
             * ContactPoint przechowuje dane kontaktowe użytkownika dla różnych kanałów:
             * email, SMS, push.
             */
            ContactPoint contactPoint = new ContactPoint(
                    cp.email(),
                    cp.phoneNumber(),
                    cp.pushToken()
            );

            /*
             * Kontroler nie wysyła powiadomienia samodzielnie.
             * Deleguje całą logikę do NotificationService.
             *
             * Service:
             * - sprawdza limity,
             * - sprawdza idempotencyKey,
             * - wykonuje deduplikację,
             * - wybiera kanały,
             * - waliduje payload względem template’ów,
             * - zapisuje Notification,
             * - zapisuje OutboxEvent.
             */
            return ApiDtos.CreateNotificationResponse.from(
                    notificationService.create(
                            TenantContext.getTenantId(),
                            actor,
                            request.userId(),
                            request.notificationType(),
                            request.channels(),
                            contactPoint,
                            request.payload(),
                            request.idempotencyKey(),
                            request.expiresAt()
                    )
            );
        }

        /**
         * Zwraca wszystkie powiadomienia dla aktualnego tenanta.
         *
         * Tenant jest pobierany z TenantContext, więc użytkownik jednego tenanta
         * nie powinien widzieć danych innego tenanta.
         */
        @GetMapping
        public List<ApiDtos.NotificationResponse> findAll() {
            return notificationRepository
                    .findAll(TenantContext.getTenantId())
                    .stream()
                    .map(ApiDtos.NotificationResponse::from)
                    .toList();
        }

        /**
         * Pobiera jedno powiadomienie po ID.
         *
         * Szukanie również jest zawężone do aktualnego tenanta.
         * Jeśli powiadomienie nie istnieje w ramach tego tenanta, zwracamy 404.
         */
        @GetMapping("/{id}")
        public ApiDtos.NotificationResponse findById(@PathVariable UUID id) {
            return notificationRepository
                    .findById(TenantContext.getTenantId(), id)
                    .map(ApiDtos.NotificationResponse::from)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Notification not found"
                    ));
        }

        /**
         * Zwraca joby utworzone dla konkretnego powiadomienia.
         *
         * Jeden Notification może mieć wiele jobów, np.:
         * - EMAIL job,
         * - SMS job,
         * - PUSH job,
         * - IN_APP job.
         *
         * To pozwala sprawdzić status każdego kanału niezależnie.
         */
        @GetMapping("/{id}/jobs")
        public List<ApiDtos.NotificationJobResponse> findJobs(@PathVariable UUID id) {
            return jobRepository
                    .findByNotificationId(id)
                    .stream()
                    .map(ApiDtos.NotificationJobResponse::from)
                    .toList();
        }

        /**
         * Anuluje powiadomienie.
         *
         * Anulowanie ma sens głównie zanim powiadomienie zostanie przetworzone.
         * Sama walidacja, czy status pozwala na cancel, znajduje się w domenie/serwisie,
         * a nie w kontrolerze.
         */
        @DeleteMapping("/{id}")
        public ApiDtos.NotificationResponse cancel(
                @RequestHeader(value = "X-Actor", required = false) String actor,
                @PathVariable UUID id
        ) {
            return ApiDtos.NotificationResponse.from(
                    notificationService.cancel(TenantContext.getTenantId(), actor, id)
            );
        }
    }

    /**
     * Kontroler administracyjny do zarządzania template’ami.
     *
     * Template określa treść wiadomości dla konkretnego:
     * - tenanta,
     * - typu powiadomienia,
     * - kanału,
     * - wersji.
     */
    @RestController
    @RequestMapping("/api/v1/admin/templates")
    public static class AdminTemplateController {
        private final Ports.TemplateRepository templateRepository;
        private final TemplateManagementService templateService;

        public AdminTemplateController(
                Ports.TemplateRepository templateRepository,
                TemplateManagementService templateService
        ) {
            this.templateRepository = templateRepository;
            this.templateService = templateService;
        }

        /**
         * Lista template’ów dla aktualnego tenanta.
         *
         * Używane przez panel administracyjny lub narzędzia operatorskie.
         */
        @GetMapping
        public List<ApiDtos.TemplateResponse> findAll() {
            return templateRepository
                    .findAll(TenantContext.getTenantId())
                    .stream()
                    .map(ApiDtos.TemplateResponse::from)
                    .toList();
        }

        /**
         * Tworzy nowy template.
         *
         * requiredVariables określa, jakich pól system oczekuje w payloadzie.
         * Przykład:
         * template używa {{firstName}} i {{resetLink}},
         * więc requiredVariables powinno zawierać firstName i resetLink.
         */
        @PostMapping
        public ApiDtos.TemplateResponse create(
                @RequestHeader(value = "X-Actor", required = false) String actor,
                @Valid @RequestBody ApiDtos.TemplateRequest request
        ) {
            Set<String> vars = request.requiredVariables() == null
                    ? Set.of()
                    : request.requiredVariables();

            return ApiDtos.TemplateResponse.from(
                    templateService.create(
                            TenantContext.getTenantId(),
                            actor,
                            request.templateKey(),
                            request.notificationType(),
                            request.channel(),
                            request.subject(),
                            request.body(),
                            vars
                    )
            );
        }

        /**
         * Aktualizuje istniejący template.
         *
         * W tej implementacji update podbija wersję template’u.
         * To ważne, bo w systemach notyfikacji template’y powinny być wersjonowane,
         * a nie bezrefleksyjnie nadpisywane.
         */
        @PutMapping("/{id}")
        public ApiDtos.TemplateResponse update(
                @RequestHeader(value = "X-Actor", required = false) String actor,
                @PathVariable UUID id,
                @Valid @RequestBody ApiDtos.TemplateRequest request
        ) {
            Set<String> vars = request.requiredVariables() == null
                    ? Set.of()
                    : request.requiredVariables();

            return ApiDtos.TemplateResponse.from(
                    templateService.update(
                            TenantContext.getTenantId(),
                            actor,
                            id,
                            request.subject(),
                            request.body(),
                            vars,
                            true
                    )
            );
        }

        /**
         * Usuwa template logicznie.
         *
         * W praktyce template nie powinien być fizycznie kasowany,
         * bo może być potrzebny do audytu lub analizy historycznych powiadomień.
         * Dlatego service może go dezaktywować zamiast usuwać z pamięci/bazy.
         */
        @DeleteMapping("/{id}")
        public void delete(
                @RequestHeader(value = "X-Actor", required = false) String actor,
                @PathVariable UUID id
        ) {
            templateService.delete(TenantContext.getTenantId(), actor, id);
        }
    }

    /**
     * Kontroler preferencji użytkownika.
     *
     * Preferencje decydują, którymi kanałami użytkownik chce otrzymywać
     * konkretne typy powiadomień.
     */
    @RestController
    @RequestMapping("/api/v1/users/{userId}/notification-preferences")
    public static class PreferenceController {
        private final Ports.PreferenceService preferenceService;

        public PreferenceController(Ports.PreferenceService preferenceService) {
            this.preferenceService = preferenceService;
        }

        /**
         * Pobiera preferencje użytkownika dla aktualnego tenanta.
         *
         * Wynik ma strukturę:
         * NotificationType -> Channel -> enabled/disabled.
         */
        @GetMapping
        public Map<NotificationType, Map<Channel, Boolean>> get(@PathVariable String userId) {
            return preferenceService.getPreferences(
                    TenantContext.getTenantId(),
                    userId
            );
        }

        /**
         * Aktualizuje preferencje użytkownika.
         *
         * Te preferencje później wpływają na wybór kanałów w NotificationService.
         * Przykład: użytkownik może wyłączyć marketing przez EMAIL,
         * ale nadal dostawać PUSH i IN_APP.
         */
        @PutMapping
        public Map<NotificationType, Map<Channel, Boolean>> update(
                @PathVariable String userId,
                @Valid @RequestBody ApiDtos.UpdatePreferencesRequest request
        ) {
            return preferenceService.updatePreferences(
                    TenantContext.getTenantId(),
                    userId,
                    request.preferences()
            );
        }
    }

    /**
     * Kontroler webhooków od providerów.
     *
     * Providerzy typu SendGrid, Twilio, FCM mogą asynchronicznie informować system,
     * że wiadomość została dostarczona, odbita albo zakończona błędem.
     */
    @RestController
    @RequestMapping("/api/v1/provider-webhooks")
    public static class ProviderWebhookController {
        private final ProviderWebhookService webhookService;

        public ProviderWebhookController(ProviderWebhookService webhookService) {
            this.webhookService = webhookService;
        }

        /**
         * Przyjmuje status wiadomości od zewnętrznego providera.
         *
         * Kluczowe pole to providerMessageId.
         * Po nim system znajduje konkretny NotificationJob i aktualizuje jego status.
         *
         * W prawdziwej produkcji ten endpoint musi jeszcze walidować podpis webhooka.
         */
        @PostMapping
        public Map<String, Object> handle(
                @Valid @RequestBody ApiDtos.ProviderWebhookRequest request
        ) {
            webhookService.handle(
                    request.providerMessageId(),
                    request.status(),
                    request.reason()
            );

            return Map.of("accepted", true);
        }
    }

    /**
     * Kontroler administracyjny kampanii.
     *
     * Kampania to masowe utworzenie powiadomień dla wielu użytkowników.
     */
    @RestController
    @RequestMapping("/api/v1/admin/campaigns")
    public static class AdminCampaignController {
        private final Ports.CampaignRepository campaignRepository;
        private final CampaignService campaignService;

        public AdminCampaignController(
                Ports.CampaignRepository campaignRepository,
                CampaignService campaignService
        ) {
            this.campaignRepository = campaignRepository;
            this.campaignService = campaignService;
        }

        /**
         * Tworzy kampanię, ale jej jeszcze nie uruchamia.
         *
         * Campaign zawiera:
         * - tenantId,
         * - nazwę,
         * - typ powiadomienia,
         * - listę użytkowników,
         * - kanały,
         * - payload wspólny dla wiadomości.
         */
        @PostMapping
        public Campaign create(
                @RequestHeader(value = "X-Actor", required = false) String actor,
                @Valid @RequestBody ApiDtos.CreateCampaignRequest request
        ) {
            Campaign campaign = new Campaign(
                    UUID.randomUUID(),
                    TenantContext.getTenantId(),
                    request.name(),
                    request.notificationType(),
                    request.userIds(),
                    request.channels(),
                    request.payload()
            );

            return campaignService.create(campaign, actor);
        }

        /**
         * Uruchamia kampanię.
         *
         * CampaignService tworzy osobne powiadomienie dla każdego użytkownika.
         *
         * W tej wersji contact pointy są symulowane:
         * - email: userId@example.com,
         * - telefon: przykładowy numer,
         * - push token: push-userId.
         *
         * W produkcji dane kontaktowe powinny pochodzić z User/Profile Service.
         */
        @PostMapping("/{id}/start")
        public Campaign start(
                @RequestHeader(value = "X-Actor", required = false) String actor,
                @PathVariable UUID id
        ) {
            return campaignService.start(
                    TenantContext.getTenantId(),
                    actor,
                    id,
                    userId -> new ContactPoint(
                            userId + "@example.com",
                            "+48123123123",
                            "push-" + userId
                    )
            );
        }

        /**
         * Zwraca kampanie dla aktualnego tenanta.
         */
        @GetMapping
        public List<Campaign> findAll() {
            return campaignRepository.findAll(TenantContext.getTenantId());
        }
    }

    /**
     * Kontroler digestów.
     *
     * Digest pozwala zebrać wiele małych zdarzeń i wysłać jedno zbiorcze
     * powiadomienie zamiast spamować użytkownika wieloma wiadomościami.
     */
    @RestController
    @RequestMapping("/api/v1/digests")
    public static class DigestController {
        private final DigestService digestService;

        public DigestController(DigestService digestService) {
            this.digestService = digestService;
        }

        /**
         * Dodaje pojedyncze zdarzenie do bufora digestu.
         *
         * Przykład:
         * zamiast wysyłać 10 osobnych powiadomień o komentarzach,
         * system buforuje je pod digestKey = "comments",
         * a scheduler później wyśle jedno zbiorcze powiadomienie.
         */
        @PostMapping("/buffer")
        public DigestBuffer buffer(
                @RequestHeader(value = "X-Actor", required = false) String actor,
                @Valid @RequestBody ApiDtos.BufferDigestRequest request
        ) {
            return digestService.buffer(
                    TenantContext.getTenantId(),
                    actor,
                    request.userId(),
                    request.digestKey(),
                    request.item()
            );
        }
    }

    /**
     * Kontroler operacyjny dla administratorów i operatorów systemu.
     *
     * Udostępnia techniczny podgląd:
     * - kolejki,
     * - jobów,
     * - DLQ,
     * - outboxa,
     * - audytu.
     */
    @RestController
    @RequestMapping("/api/v1/admin/ops")
    public static class AdminOpsController {
        private final Ports.NotificationQueue queue;
        private final Ports.NotificationJobRepository jobRepository;
        private final Ports.OutboxRepository outboxRepository;
        private final Ports.AuditRepository auditRepository;

        public AdminOpsController(
                Ports.NotificationQueue queue,
                Ports.NotificationJobRepository jobRepository,
                Ports.OutboxRepository outboxRepository,
                Ports.AuditRepository auditRepository
        ) {
            this.queue = queue;
            this.jobRepository = jobRepository;
            this.outboxRepository = outboxRepository;
            this.auditRepository = auditRepository;
        }

        /**
         * Prosty dashboard techniczny.
         *
         * Pokazuje najważniejsze liczniki operacyjne:
         * - rozmiar kolejki,
         * - liczbę jobów,
         * - liczbę jobów w DLQ,
         * - liczbę eventów outbox,
         * - liczbę eventów audytowych.
         *
         * To nie zastępuje Grafany/Prometheusa, ale daje szybki podgląd.
         */
        @GetMapping("/dashboard")
        public Map<String, Object> dashboard() {
            String tenantId = TenantContext.getTenantId();

            return Map.of(
                    "tenantId", tenantId,
                    "queueSize", queue.size(),
                    "jobs", jobRepository.findAll(tenantId).size(),
                    "dlq", jobRepository.findDeadLetterJobs(tenantId).size(),
                    "outbox", outboxRepository.findAll(tenantId).size(),
                    "auditEvents", auditRepository.findAll(tenantId).size()
            );
        }

        /**
         * Lista wszystkich jobów dla aktualnego tenanta.
         *
         * Przydatne do debugowania, czy worker przetworzył powiadomienia
         * i jaki status ma każdy kanał.
         */
        @GetMapping("/jobs")
        public List<ApiDtos.NotificationJobResponse> jobs() {
            return jobRepository
                    .findAll(TenantContext.getTenantId())
                    .stream()
                    .map(ApiDtos.NotificationJobResponse::from)
                    .toList();
        }

        /**
         * Lista jobów w Dead Letter Queue.
         *
         * DLQ zawiera joby, których nie udało się przetworzyć po retry
         * albo które zakończyły się błędem permanentnym.
         */
        @GetMapping("/dlq")
        public List<ApiDtos.NotificationJobResponse> dlq() {
            return jobRepository
                    .findDeadLetterJobs(TenantContext.getTenantId())
                    .stream()
                    .map(ApiDtos.NotificationJobResponse::from)
                    .toList();
        }

        /**
         * Podgląd eventów outboxa.
         *
         * Outbox jest kluczowy dla niezawodności:
         * najpierw zapisujemy Notification + OutboxEvent,
         * a dopiero później publisher tworzy joby i wrzuca je do kolejki.
         */
        @GetMapping("/outbox")
        public List<OutboxEvent> outbox() {
            return outboxRepository.findAll(TenantContext.getTenantId());
        }

        /**
         * Podgląd audytu.
         *
         * Audyt pokazuje ważne operacje biznesowe i techniczne,
         * np. utworzenie powiadomienia, retry, wysyłkę, bounce,
         * zmianę template’u lub preferencji.
         */
        @GetMapping("/audit")
        public List<AuditEvent> audit() {
            return auditRepository.findAll(TenantContext.getTenantId());
        }
    }
}
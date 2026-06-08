package com.example.notification.application;

import com.example.notification.domain.*;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Serwis odpowiedzialny za obsługę kampanii powiadomień.
 *
 * Kampania to mechanizm masowego utworzenia powiadomień dla wielu użytkowników.
 *
 * Przykład:
 * - kampania marketingowa do 10 000 użytkowników,
 * - informacja produktowa,
 * - komunikat administracyjny,
 * - promocja,
 * - zbiorczy alert.
 *
 * Ważne:
 * Ten serwis sam nie wysyła wiadomości do providerów.
 * On uruchamia kampanię poprzez tworzenie zwykłych Notification dla każdego użytkownika.
 *
 * Dalej standardowy pipeline robi resztę:
 * NotificationService -> OutboxEvent -> OutboxPublisher -> Queue -> NotificationWorker -> Provider.
 */
@Service
public class CampaignService {
    private final Ports.CampaignRepository campaignRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public CampaignService(
            Ports.CampaignRepository campaignRepository,
            NotificationService notificationService,
            AuditService auditService
    ) {
        this.campaignRepository = campaignRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    /**
     * Tworzy kampanię, ale jej jeszcze nie uruchamia.
     *
     * Na tym etapie kampania jest tylko zapisana w repozytorium.
     * Nie powstają jeszcze żadne Notification ani NotificationJob.
     *
     * Typowy status po utworzeniu: CREATED.
     */
    public Campaign create(Campaign campaign, String actor) {
        /*
         * Zapisujemy definicję kampanii.
         *
         * Campaign zawiera m.in.:
         * - tenantId,
         * - nazwę kampanii,
         * - notificationType,
         * - listę userIds,
         * - kanały,
         * - wspólny payload.
         */
        campaignRepository.save(campaign);

        /*
         * Audytujemy utworzenie kampanii.
         *
         * actor mówi, kto wykonał operację, np. admin albo marketer.
         * To jest istotne przy kampaniach, bo mogą wysyłać dużo wiadomości.
         */
        auditService.record(
                campaign.getTenantId(),
                actor,
                AuditAction.CAMPAIGN_CREATED,
                campaign.getId(),
                Map.of("name", campaign.getName())
        );

        return campaign;
    }

    /**
     * Uruchamia kampanię.
     *
     * To jest najważniejsza metoda w tej klasie.
     *
     * Dla każdego użytkownika z kampanii tworzy osobne Notification,
     * wykorzystując normalny NotificationService.
     *
     * Dzięki temu kampanie korzystają z tych samych mechanizmów co zwykłe powiadomienia:
     * - rate limiting,
     * - idempotencja,
     * - deduplikacja,
     * - preferencje użytkownika,
     * - walidacja template’ów,
     * - Outbox Pattern,
     * - retry,
     * - DLQ,
     * - provider fallback.
     */
    public Campaign start(
            String tenantId,
            String actor,
            UUID id,
            Function<String, ContactPoint> contactResolver
    ) {
        /*
         * Pobieramy kampanię w ramach konkretnego tenanta.
         *
         * To zabezpiecza przed sytuacją, w której tenant A próbuje uruchomić
         * kampanię należącą do tenanta B.
         */
        Campaign campaign = campaignRepository
                .findById(tenantId, id)
                .orElseThrow(() -> new Exceptions.NotificationValidationException(
                        "Campaign not found: " + id
                ));

        /*
         * Oznaczamy kampanię jako RUNNING.
         *
         * To informuje system/operatora, że kampania jest w trakcie uruchamiania.
         */
        campaign.markRunning();
        campaignRepository.save(campaign);

        /*
         * Audytujemy start kampanii.
         *
         * Zapisujemy liczbę użytkowników, bo to ważna informacja operacyjna:
         * ile powiadomień potencjalnie zostanie utworzonych.
         */
        auditService.record(
                tenantId,
                actor,
                AuditAction.CAMPAIGN_STARTED,
                campaign.getId(),
                Map.of("users", campaign.getUserIds().size())
        );

        /*
         * Dla każdego użytkownika tworzymy osobne Notification.
         *
         * To ważna decyzja architektoniczna:
         * kampania nie jest jednym wielkim jobem.
         * Kampania rozbija się na wiele normalnych powiadomień.
         *
         * Dzięki temu każde powiadomienie:
         * - ma własny status,
         * - ma własne joby per kanał,
         * - może zostać retryowane niezależnie,
         * - może respektować preferencje konkretnego użytkownika,
         * - może zostać zdeduplikowane.
         */
        for (String userId : campaign.getUserIds()) {
            /*
             * contactResolver dostarcza dane kontaktowe użytkownika.
             *
             * W tej wersji projektu może to być funkcja testowa/symulowana.
             * W produkcji ten resolver powinien pobierać dane z User/Profile Service
             * albo z lokalnej kopii contact points.
             */
            ContactPoint contactPoint = contactResolver.apply(userId);

            /*
             * Tworzymy Notification dla konkretnego użytkownika.
             *
             * Idempotency key jest generowany per kampania + użytkownik:
             *
             * campaign:{campaignId}:{userId}
             *
             * Dzięki temu ponowne uruchomienie tej samej kampanii
             * nie powinno utworzyć drugiego powiadomienia dla tego samego usera.
             */
            notificationService.create(
                    tenantId,
                    actor,
                    userId,
                    campaign.getNotificationType(),
                    campaign.getChannels(),
                    contactPoint,
                    campaign.getPayload(),
                    "campaign:" + campaign.getId() + ":" + userId,
                    null
            );
        }

        /*
         * Jeśli pętla przeszła bez wyjątku, oznaczamy kampanię jako COMPLETED.
         *
         * Ważne:
         * COMPLETED tutaj oznacza, że system utworzył Notification dla użytkowników.
         * Nie oznacza, że wiadomości zostały już dostarczone.
         *
         * Dostarczenie dzieje się później asynchronicznie przez:
         * OutboxPublisher -> Queue -> NotificationWorker -> Provider.
         */
        campaign.markCompleted();
        campaignRepository.save(campaign);

        /*
         * Audytujemy zakończenie uruchamiania kampanii.
         */
        auditService.record(
                tenantId,
                actor,
                AuditAction.CAMPAIGN_COMPLETED,
                campaign.getId(),
                Map.of()
        );

        return campaign;
    }
}
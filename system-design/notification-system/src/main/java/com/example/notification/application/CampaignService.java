package com.example.notification.application;

import com.example.notification.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

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
    private final int batchSize;
    private final Clock clock;
    private final Duration claimLease;

    public CampaignService(
            Ports.CampaignRepository campaignRepository,
            NotificationService notificationService,
            AuditService auditService,
            Clock clock,
            @Value("${notification.campaign.batch-size:100}") int batchSize,
            @Value("${notification.campaign.claim-seconds:60}") long claimSeconds
    ) {
        this.campaignRepository = campaignRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.batchSize = batchSize;
        this.clock = clock;
        this.claimLease = Duration.ofSeconds(claimSeconds);
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
        Instant now = clock.instant();
        Ports.CampaignRepository.Claim claim = campaignRepository
                .claim(tenantId, id, now, now.plus(claimLease))
                .orElseThrow(() -> new Exceptions.NotificationValidationException(
                        "Campaign is missing, completed or already claimed: " + id));
        Campaign campaign = claim.campaign();

        /*
         * Oznaczamy kampanię jako RUNNING.
         *
         * To informuje system/operatora, że kampania jest w trakcie uruchamiania.
         */
        campaign.markRunning();
        if (!campaignRepository.checkpoint(claim)) throw lostClaim(id);

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
        int stopExclusive = Math.min(campaign.getNextRecipientIndex() + batchSize, campaign.getUserIds().size());
        while (campaign.getNextRecipientIndex() < stopExclusive) {
            String userId = campaign.getUserIds().get(campaign.getNextRecipientIndex());
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
            try {
                notificationService.create(
                        tenantId, actor, userId, campaign.getNotificationType(), campaign.getChannels(), contactPoint,
                        campaign.getPayload(), "campaign:" + campaign.getId() + ":" + userId, null);
                campaign.recipientProcessed();
            } catch (RuntimeException recipientFailure) {
                // Checkpoint przesuwa się również po błędzie: operator może naprawić
                // pojedynczego odbiorcę bez ponawiania całej kampanii.
                campaign.recipientFailed();
            }
            if (!campaignRepository.checkpoint(claim)) throw lostClaim(id);
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
        if (!campaign.hasMoreRecipients()) campaign.markCompleted();
        if (!campaignRepository.checkpoint(claim)) throw lostClaim(id);
        campaignRepository.release(claim);

        /*
         * Audytujemy zakończenie uruchamiania kampanii.
         */
        if (!campaign.hasMoreRecipients()) {
            auditService.record(tenantId, actor, AuditAction.CAMPAIGN_COMPLETED,
                    campaign.getId(), Map.of("failedRecipients", campaign.getFailedRecipients()));
        }

        return campaign;
    }

    private IllegalStateException lostClaim(UUID campaignId) {
        return new IllegalStateException("Campaign claim lost while checkpointing: " + campaignId);
    }
}

package com.example.filestorage.production.outbox;

import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Operacyjny REST controller dla mechanizmu transactional outbox.
 *
 * To nie jest API dla zwykłych użytkowników aplikacji.
 * To endpoint techniczny dla ops/admin/monitoringu.
 *
 * Pozwala podejrzeć:
 * - ile eventów czeka na publikację,
 * - ile zostało opublikowanych,
 * - ile zakończyło się błędem,
 * - jakie eventy trafiły do DLQ.
 *
 * W produkcji endpointy pod /api/v1/ops/* powinny być zabezpieczone
 * osobną rolą administracyjną albo dostępne tylko wewnętrznie.
 */
@RestController
@RequestMapping("/api/v1/ops/outbox")
public class OutboxController {

    /**
     * Repozytorium eventów outboxa.
     *
     * OutboxEvent reprezentuje zdarzenie zapisane w tej samej transakcji
     * co zmiana domenowa, np. FILE_CREATED albo FILE_DELETED.
     *
     * Worker publikuje później te eventy do brokera, webhooka albo lokalnego handlera.
     */
    private final OutboxEventRepository outboxRepository;

    /**
     * Repozytorium dead-letter queue.
     *
     * DLQ przechowuje eventy, których nie udało się poprawnie opublikować
     * albo obsłużyć po przekroczeniu limitu prób.
     */
    private final DeadLetterEventRepository deadLetterRepository;

    public OutboxController(OutboxEventRepository outboxRepository,
                            DeadLetterEventRepository deadLetterRepository) {
        this.outboxRepository = outboxRepository;
        this.deadLetterRepository = deadLetterRepository;
    }

    /**
     * Zwraca podstawowe statystyki outboxa.
     *
     * Endpoint:
     * GET /api/v1/ops/outbox/stats
     *
     * Wynik:
     * - pending: eventy czekające na publikację,
     * - published: eventy poprawnie opublikowane,
     * - failed: eventy, które zakończyły się błędem.
     *
     * Taki endpoint może być użyty przez prosty dashboard operacyjny
     * albo health-check bardziej szczegółowy niż standardowe /actuator/health.
     */
    @GetMapping("/stats")
    public Map<String, Long> stats() {
        return Map.of(
                /*
                 * Eventy, które jeszcze nie zostały przetworzone przez outbox worker.
                 * Jeśli liczba stale rośnie, worker może nie działać albo broker jest niedostępny.
                 */
                "pending", outboxRepository.countByStatus(OutboxStatus.PENDING),

                /*
                 * Eventy zakończone sukcesem.
                 * Przydatne do ogólnego monitoringu przepływu zdarzeń.
                 */
                "published", outboxRepository.countByStatus(OutboxStatus.PUBLISHED),

                /*
                 * Eventy oznaczone jako failed.
                 * Wysoka liczba failed wymaga sprawdzenia logów i DLQ.
                 */
                "failed", outboxRepository.countByStatus(OutboxStatus.FAILED)
        );
    }

    /**
     * Zwraca wpisy z dead-letter queue.
     *
     * Endpoint:
     * GET /api/v1/ops/outbox/dlq?page=0&size=20
     *
     * DLQ jest miejscem, do którego trafiają eventy nieobsłużone poprawnie
     * po retry albo po trwałym błędzie.
     *
     * Wyniki są sortowane malejąco po failedAt,
     * czyli najnowsze awarie są widoczne jako pierwsze.
     */
    @GetMapping("/dlq")
    public Object dlq(@RequestParam(defaultValue = "0") int page,
                      @RequestParam(defaultValue = "20") int size) {
        /*
         * Bezpieczna paginacja:
         * - page nie może być mniejszy niż 0,
         * - size ma zakres 1..100.
         *
         * To chroni endpoint operacyjny przed przypadkowym pobraniem
         * bardzo dużej liczby rekordów.
         */
        return deadLetterRepository.findAllByOrderByFailedAtDesc(
                PageRequest.of(
                        Math.max(page, 0),
                        Math.min(Math.max(size, 1), 100)
                )
        );
    }
}
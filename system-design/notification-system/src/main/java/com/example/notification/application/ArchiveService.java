package com.example.notification.application;

import com.example.notification.domain.AuditAction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Serwis odpowiedzialny za okresową archiwizację starych danych.
 *
 * W kontekście Notification System ten komponent pełni rolę joba maintenance.
 *
 * Główne zadania:
 * - znaleźć stare powiadomienia w statusach końcowych,
 * - oznaczyć je jako ARCHIVED,
 * - znaleźć stare wpisy audytowe,
 * - usunąć je z repozytorium,
 * - zapisać event audytowy informujący, że archiwizacja się wykonała.
 *
 * Ważne:
 * Ten serwis nie bierze udziału w bieżącej wysyłce powiadomień.
 * Jego zadaniem jest kontrolowanie wzrostu danych historycznych.
 */
@Service
public class ArchiveService {
    private final Ports.NotificationRepository notificationRepository;
    private final Ports.AuditRepository auditRepository;
    private final AuditService auditService;

    /**
     * Czas retencji danych liczony w sekundach.
     *
     * Jeśli retentionSeconds = 120, to serwis uzna za stare dane,
     * które są starsze niż 120 sekund.
     *
     * W tej wersji projektu wartość jest mała, żeby łatwo zobaczyć działanie lokalnie.
     * W produkcji byłyby to raczej dni albo miesiące, np. 30, 90 albo 180 dni.
     */
    private final long retentionSeconds;

    public ArchiveService(
            Ports.NotificationRepository notificationRepository,
            Ports.AuditRepository auditRepository,
            AuditService auditService,
            @Value("${notification.archive.retention-seconds:120}") long retentionSeconds
    ) {
        this.notificationRepository = notificationRepository;
        this.auditRepository = auditRepository;
        this.auditService = auditService;
        this.retentionSeconds = retentionSeconds;
    }

    /**
     * Cykliczny job archiwizujący stare dane.
     *
     * Scheduler uruchamia tę metodę co określony czas:
     *
     * notification.archive.fixed-delay-ms
     *
     * Przykład z application.yml:
     *
     * notification:
     *   archive:
     *     fixed-delay-ms: 10000
     *
     * To oznacza, że metoda wykona się ponownie 10 sekund po zakończeniu
     * poprzedniego przebiegu.
     */
    @Scheduled(fixedDelayString = "${notification.archive.fixed-delay-ms:10000}")
    public void archiveOldData() {
        /*
         * Wyliczamy granicę retencji.
         *
         * Wszystko starsze niż threshold kwalifikuje się do archiwizacji/usunięcia.
         *
         * Przykład:
         * - teraz: 12:00:00,
         * - retentionSeconds: 120,
         * - threshold: 11:58:00.
         *
         * Dane starsze niż 11:58:00 uznajemy za stare.
         */
        Instant threshold = Instant.now().minusSeconds(retentionSeconds);

        /*
         * Pobieramy stare powiadomienia w statusach końcowych.
         *
         * Ważne: archiwizujemy tylko terminalne powiadomienia, czyli takie,
         * których proces już się zakończył.
         *
         * Typowe statusy terminalne:
         * - DELIVERED,
         * - BOUNCED,
         * - FAILED,
         * - EXPIRED,
         * - CANCELLED.
         *
         * Nie wolno archiwizować aktywnych powiadomień typu QUEUED albo PROCESSING,
         * bo worker mógłby jeszcze próbować je wysłać.
         */
        var notifications = notificationRepository.findTerminalOlderThan(threshold);

        /*
         * Oznaczamy każde stare terminalne powiadomienie jako ARCHIVED.
         *
         * W tej implementacji nie usuwamy powiadomień fizycznie.
         * Zmieniamy ich status na ARCHIVED.
         *
         * To jest bezpieczniejsze niż delete, bo:
         * - zachowujemy historię,
         * - można nadal debugować,
         * - nie gubimy śladu biznesowego.
         */
        notifications.forEach(n -> {
            n.markArchived();
            notificationRepository.save(n);
        });

        /*
         * Pobieramy stare wpisy audytowe.
         *
         * W tej uproszczonej implementacji stare audyty są usuwane fizycznie.
         *
         * Produkcyjnie byłbym z tym ostrożny:
         * audyt często trzeba trzymać dłużej niż same dane operacyjne,
         * szczególnie przy wymaganiach compliance.
         */
        var oldAudit = auditRepository.findOlderThan(threshold);

        /*
         * Usuwamy stare wpisy audytowe.
         *
         * W realnym systemie lepszym rozwiązaniem byłoby:
         * - przeniesienie do cold storage,
         * - eksport do S3 / Blob Storage,
         * - partycjonowanie tabel po czasie,
         * - albo osobna polityka retencji per tenant.
         */
        oldAudit.forEach(auditRepository::delete);

        /*
         * Jeśli cokolwiek zostało zarchiwizowane lub usunięte,
         * zapisujemy informację o przebiegu archiwizacji.
         *
         * Dzięki temu operator może sprawdzić:
         * - kiedy archiwizacja się wykonała,
         * - ile powiadomień oznaczono jako ARCHIVED,
         * - ile eventów audytowych usunięto.
         */
        if (!notifications.isEmpty() || !oldAudit.isEmpty()) {
            auditService.record(
                    "system",
                    "system",
                    AuditAction.ARCHIVE_RUN,
                    null,
                    Map.of(
                            "archivedNotifications", notifications.size(),
                            "deletedAuditEvents", oldAudit.size()
                    )
            );
        }
    }
}
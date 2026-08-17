package com.example.filestorage.production.ops;

import com.example.filestorage.production.backup.BackupRunRepository;
import com.example.filestorage.production.backup.BackupService;
import com.example.filestorage.production.gc.GarbageCollectionService;
import com.example.filestorage.production.processing.FileProcessingJobRepository;
import com.example.filestorage.production.processing.FileProcessingJobStatus;
import com.example.filestorage.production.outbox.OutboxEventRepository;
import com.example.filestorage.production.outbox.OutboxStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Operacyjny controller administracyjny.
 *
 * Endpointy pod /api/v1/ops nie są częścią zwykłego API użytkownika.
 * Służą do monitoringu, diagnostyki i uruchamiania wybranych operacji utrzymaniowych.
 *
 * Ten controller agreguje informacje z kilku produkcyjnych obszarów:
 * - background processing plików,
 * - transactional outbox,
 * - backupy,
 * - garbage collection.
 *
 * W produkcji wszystkie endpointy tego controllera powinny być zabezpieczone
 * rolą admin/internal albo dostępne wyłącznie z sieci wewnętrznej.
 */
@RestController
@RequestMapping("/api/v1/ops")
public class OperationsController {

    /**
     * Repozytorium jobów przetwarzania plików.
     *
     * Używane do policzenia jobów PENDING i FAILED,
     * np. antywirusa, miniaturek i deduplikacji.
     */
    private final FileProcessingJobRepository jobRepository;

    /**
     * Repozytorium eventów outboxa.
     *
     * Używane do sprawdzania, czy eventy domenowe oczekują na publikację
     * albo zakończyły się błędem.
     */
    private final OutboxEventRepository outboxRepository;

    /**
     * Serwis backupu.
     *
     * W tym controllerze używany do utworzenia manifestu backupu metadanych.
     */
    private final BackupService backupService;

    /**
     * Repozytorium historii backupów.
     *
     * Pozwala listować poprzednie uruchomienia backupów.
     */
    private final BackupRunRepository backupRunRepository;

    /**
     * Serwis garbage collection.
     *
     * Używany do pobrania statystyk GC, np. liczby osieroconych blobów
     * albo kandydatów do usunięcia.
     */
    private final GarbageCollectionService garbageCollectionService;

    public OperationsController(FileProcessingJobRepository jobRepository,
                                OutboxEventRepository outboxRepository,
                                BackupService backupService,
                                BackupRunRepository backupRunRepository,
                                GarbageCollectionService garbageCollectionService) {
        this.jobRepository = jobRepository;
        this.outboxRepository = outboxRepository;
        this.backupService = backupService;
        this.backupRunRepository = backupRunRepository;
        this.garbageCollectionService = garbageCollectionService;
    }

    /**
     * Zwraca skrócone podsumowanie stanu operacyjnego aplikacji.
     *
     * Endpoint:
     * GET /api/v1/ops/health-summary
     *
     * To nie zastępuje pełnego /actuator/health.
     * Jest bardziej domenowym podsumowaniem:
     * - ile jobów processingu czeka,
     * - ile jobów processingu padło,
     * - ile eventów outboxa czeka,
     * - ile eventów outboxa padło,
     * - jaki jest stan garbage collection.
     *
     * Ten endpoint nadaje się do prostego dashboardu operatorskiego.
     */
    @GetMapping("/health-summary")
    public Map<String, Object> healthSummary() {
        return Map.of(
                /*
                 * Liczba jobów processingu czekających na wykonanie.
                 * Jeśli stale rośnie, worker processingu może nie działać
                 * albo przetwarzanie trwa zbyt długo.
                 */
                "processingPending", jobRepository.countByStatus(FileProcessingJobStatus.PENDING),

                /*
                 * Liczba jobów processingu zakończonych błędem.
                 * Wysoka wartość może oznaczać problem z antywirusem,
                 * generowaniem miniaturek, storage albo deduplikacją.
                 */
                "processingFailed", jobRepository.countByStatus(FileProcessingJobStatus.FAILED),

                /*
                 * Liczba eventów outboxa oczekujących na publikację.
                 * Rosnąca kolejka może oznaczać problem z outbox workerem
                 * albo brokerem wiadomości.
                 */
                "outboxPending", outboxRepository.countByStatus(OutboxStatus.PENDING),

                /*
                 * Liczba eventów outboxa zakończonych błędem.
                 * Takie eventy wymagają analizy logów albo DLQ.
                 */
                "outboxFailed", outboxRepository.countByStatus(OutboxStatus.FAILED),

                /*
                 * Statystyki garbage collection.
                 * Szczegóły zależą od implementacji GarbageCollectionService.stats().
                 */
                "gc", garbageCollectionService.stats()
        );
    }

    /**
     * Tworzy manifest backupu metadanych.
     *
     * Endpoint:
     * POST /api/v1/ops/backups/metadata-manifest
     *
     * Manifest backupu zwykle opisuje:
     * - kiedy backup został uruchomiony,
     * - jakie tabele/metadane obejmuje,
     * - gdzie zapisano wynik,
     * - status backupu,
     * - ewentualny checksum albo rozmiar.
     *
     * To jest operacja administracyjna.
     * Nie powinna być dostępna dla zwykłych użytkowników.
     */
    @PostMapping("/backups/metadata-manifest")
    public Object createBackupManifest() {
        return backupService.createMetadataBackupManifest();
    }

    /**
     * Listuje historię uruchomień backupów.
     *
     * Endpoint:
     * GET /api/v1/ops/backups?page=0&size=20
     *
     * Wyniki są sortowane malejąco po startedAt,
     * czyli najnowsze backupy są widoczne jako pierwsze.
     */
    @GetMapping("/backups")
    public Object backups(@RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "20") int size) {
        /*
         * Bezpieczna paginacja:
         * - page minimum 0,
         * - size w zakresie 1..100.
         *
         * Chroni endpoint operacyjny przed przypadkowym pobraniem
         * zbyt dużej liczby rekordów.
         */
        return backupRunRepository.findAllByOrderByStartedAtDesc(
                PageRequest.of(
                        Math.max(page, 0),
                        Math.min(Math.max(size, 1), 100)
                )
        );
    }
}
package com.example.filestorage.production.gc;

import com.example.filestorage.upload.UploadSessionRepository;
import com.example.filestorage.upload.UploadStatus;
import com.example.filestorage.production.processing.FileProcessingJobRepository;
import com.example.filestorage.production.processing.FileProcessingJobStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Serwis produkcyjnego garbage collection.
 *
 * Garbage collection w File Storage powinien docelowo odpowiadać za sprzątanie:
 * - wygasłych sesji uploadu,
 * - tymczasowych chunków,
 * - osieroconych obiektów w storage,
 * - nieużywanych blobów po deduplikacji,
 * - starych artefaktów processingu,
 * - potencjalnie starych miniaturek.
 *
 * Obecna implementacja jest celowo prosta.
 * Stage 3 ma już własny cleanup upload sessions, a ta klasa daje centralne miejsce,
 * do którego można później dopinać szersze polityki lifecycle.
 */
@Service
public class GarbageCollectionService {

    /**
     * Repozytorium sesji uploadu.
     *
     * Używane do znajdowania niedokończonych sesji uploadu,
     * które wygasły i powinny zostać oznaczone jako EXPIRED.
     */
    private final UploadSessionRepository uploadSessionRepository;

    /**
     * Repozytorium jobów processingu.
     *
     * Używane tutaj tylko do statystyk operacyjnych,
     * np. liczby jobów zakończonych błędem.
     */
    private final FileProcessingJobRepository jobRepository;

    public GarbageCollectionService(UploadSessionRepository uploadSessionRepository,
                                    FileProcessingJobRepository jobRepository) {
        this.uploadSessionRepository = uploadSessionRepository;
        this.jobRepository = jobRepository;
    }

    /**
     * Cyklicznie uruchamiany garbage collection.
     *
     * Harmonogram:
     * app.production.workers.gc-fixed-delay-ms
     *
     * Domyślnie odpala się co 300000 ms, czyli co 5 minut.
     *
     * Obecnie metoda:
     * - wyszukuje maksymalnie 100 wygasłych sesji uploadu,
     * - bierze tylko statusy INITIATED i IN_PROGRESS,
     * - oznacza je jako EXPIRED.
     *
     * Uwaga:
     * ta metoda nie usuwa fizycznych chunków ze storage.
     * Jeśli Stage 3 cleanup już to robi, jest OK.
     * Jeśli nie, tutaj trzeba dodać usuwanie obiektów tymczasowych.
     */
    @Scheduled(fixedDelayString = "${app.production.workers.gc-fixed-delay-ms:300000}")
    @Transactional
    public void collect() {
        /*
         * Stage 3 ma już własny cleanup wygasłych upload sessions.
         *
         * Ten produkcyjny GC jest centralnym miejscem na przyszłe polityki:
         * - object lifecycle,
         * - cleanup blobów,
         * - cleanup miniaturek,
         * - cleanup starych rekordów jobów.
         */
        uploadSessionRepository
                .findTop100ByStatusInAndExpiresAtBefore(
                        java.util.List.of(
                                UploadStatus.INITIATED,
                                UploadStatus.IN_PROGRESS
                        ),
                        Instant.now()
                )
                .forEach(session -> {
                    /*
                     * Oznaczamy sesję jako wygasłą.
                     *
                     * To zmienia stan logiczny w DB, ale samo w sobie
                     * nie musi usuwać danych tymczasowych z object storage.
                     */
                    session.markExpired();
                    uploadSessionRepository.save(session);
                });
    }

    /**
     * Zwraca podstawowe statystyki garbage collection / processingu.
     *
     * Endpoint OperationsController.healthSummary() używa tej metody
     * jako części podsumowania stanu systemu.
     *
     * Obecnie zwracamy:
     * - liczbę failed processing jobs,
     * - timestamp sprawdzenia.
     *
     * Docelowo można dodać:
     * - liczbę wygasłych upload sessions,
     * - liczbę orphan blobs,
     * - szacowany rozmiar danych do usunięcia,
     * - liczbę miniaturek bez właściciela,
     * - ostatni czas pełnego GC.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> stats() {
        return Map.of(
                /*
                 * Liczba jobów processingu zakończonych błędem.
                 * To nie jest stricte metryka GC, ale jest przydatna operacyjnie.
                 */
                "failedProcessingJobs", jobRepository.countByStatus(FileProcessingJobStatus.FAILED),

                /*
                 * Czas wygenerowania statystyk.
                 * Obcięty do sekund, żeby response był stabilniejszy i czytelniejszy.
                 */
                "checkedAt", Instant.now()
                        .truncatedTo(ChronoUnit.SECONDS)
                        .toString()
        );
    }
}
package com.example.filestorage.production.processing;

import com.example.filestorage.file.FileMetadataRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Worker odpowiedzialny za backfill zadań przetwarzania plików.
 *
 * Jego zadaniem jest okresowe sprawdzanie ostatnio utworzonych aktywnych plików
 * i upewnianie się, że mają założone domyślne joby processingu:
 * - skan antywirusowy,
 * - generowanie miniaturki,
 * - rejestracja do deduplikacji.
 *
 * To zabezpieczenie przed sytuacją, w której np. upload zakończył się poprawnie,
 * ale z jakiegoś powodu nie zostały utworzone joby background processingu.
 */
@Component
public class ProcessingBackfillWorker {

    /**
     * Repozytorium metadanych plików.
     *
     * Worker używa go do znalezienia ostatnich aktywnych plików,
     * czyli takich, które nie są usunięte logicznie.
     */
    private final FileMetadataRepository fileRepository;

    /**
     * Serwis tworzący zadania processingu.
     *
     * Sam worker nie tworzy bezpośrednio rekordów jobów.
     * Deleguje to do FileProcessingService, który pilnuje idempotencji.
     */
    private final FileProcessingService processingService;

    public ProcessingBackfillWorker(FileMetadataRepository fileRepository,
                                    FileProcessingService processingService) {
        this.fileRepository = fileRepository;
        this.processingService = processingService;
    }

    /**
     * Okresowo uzupełnia brakujące joby dla ostatnio dodanych plików.
     *
     * Harmonogram:
     * - initialDelay = 5000 ms: pierwszy start po 5 sekundach od uruchomienia aplikacji,
     * - fixedDelay: kolejne uruchomienie po zakończeniu poprzedniego wykonania.
     *
     * Domyślnie fixedDelay wynosi 10 sekund, ale można go nadpisać konfiguracją:
     * app.production.workers.processing-fixed-delay-ms
     *
     * Metoda pobiera maksymalnie 100 najnowszych aktywnych plików.
     * Dla każdego z nich próbuje założyć domyślne joby processingu.
     *
     * To jest bezpieczne, bo FileProcessingService.enqueueDefaultJobs()
     * jest idempotentne — nie powinno tworzyć duplikatów jobów.
     */
    @Scheduled(
            fixedDelayString = "${app.production.workers.processing-fixed-delay-ms:10000}",
            initialDelay = 5000
    )
    public void enqueueMissingJobsForRecentFiles() {
        fileRepository.findTop100ByDeletedAtIsNullOrderByCreatedAtDesc()
                .forEach(file -> processingService.enqueueDefaultJobs(file.getId()));
    }
}
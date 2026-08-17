package com.example.filestorage.production.processing;

import com.example.filestorage.file.FileMetadata;
import com.example.filestorage.file.FileMetadataRepository;
import com.example.filestorage.production.antivirus.AntivirusService;
import com.example.filestorage.production.dedupe.DeduplicationService;
import com.example.filestorage.production.thumbnail.ThumbnailService;
import com.example.filestorage.storage.StorageService;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * Worker wykonujący zadania przetwarzania plików w tle.
 *
 * Ten komponent cyklicznie pobiera z bazy joby w statusie PENDING
 * i wykonuje je poza requestem użytkownika.
 *
 * Obsługiwane zadania:
 * - ANTIVIRUS_SCAN: skanowanie pliku,
 * - THUMBNAIL_GENERATION: generowanie miniaturki,
 * - DEDUPE_REGISTER: rejestracja pliku w mechanizmie deduplikacji.
 *
 * To jest element produkcyjnego pipeline’u po uploadzie.
 * Upload tworzy plik szybko, a cięższe operacje są wykonywane później.
 */
@Component
public class BackgroundProcessingWorker {

    /**
     * Repozytorium jobów processingu.
     *
     * Worker pobiera z niego zadania PENDING i zapisuje ich wynik:
     * - RUNNING,
     * - COMPLETED,
     * - FAILED.
     */
    private final FileProcessingJobRepository jobRepository;

    /**
     * Repozytorium metadanych plików.
     *
     * Potrzebne do pobrania pliku, którego dotyczy job.
     * Jeśli plik został usunięty, job nie ma czego przetwarzać.
     */
    private final FileMetadataRepository fileRepository;

    /**
     * Serwis object storage.
     *
     * Używany głównie do pobrania binarnej zawartości pliku,
     * np. przed skanem antywirusowym.
     */
    private final StorageService storageService;

    /**
     * Adapter skanowania antywirusowego.
     *
     * Może być implementacją lokalną NOOP albo integracją z ClamAV.
     */
    private final AntivirusService antivirusService;

    /**
     * Serwis generowania miniaturek.
     *
     * Powinien generować thumbnail tylko dla wspieranych typów plików,
     * np. obrazów.
     */
    private final ThumbnailService thumbnailService;

    /**
     * Serwis deduplikacji.
     *
     * Rejestruje hash/bloba pliku, żeby w przyszłości można było ograniczać
     * duplikaty danych w storage.
     */
    private final DeduplicationService deduplicationService;

    public BackgroundProcessingWorker(FileProcessingJobRepository jobRepository,
                                      FileMetadataRepository fileRepository,
                                      StorageService storageService,
                                      AntivirusService antivirusService,
                                      ThumbnailService thumbnailService,
                                      DeduplicationService deduplicationService) {
        this.jobRepository = jobRepository;
        this.fileRepository = fileRepository;
        this.storageService = storageService;
        this.antivirusService = antivirusService;
        this.thumbnailService = thumbnailService;
        this.deduplicationService = deduplicationService;
    }

    /**
     * Cyklicznie przetwarza pending joby.
     *
     * Harmonogram:
     * app.production.workers.processing-fixed-delay-ms
     *
     * Domyślnie worker odpala się co 10 sekund.
     *
     * Jednorazowo pobiera maksymalnie 25 jobów, żeby:
     * - nie zablokować aplikacji zbyt długą transakcją,
     * - ograniczyć zużycie CPU/I/O,
     * - dać szansę kolejnym cyklom przetwarzania.
     *
     * Uwaga praktyczna:
     * ta metoda ma @Transactional na całej pętli. Dla produkcji lepiej rozważyć
     * transakcję per job, żeby awaria jednego zadania nie trzymała długo blokad
     * i żeby łatwiej skalować workerów równolegle.
     */
    @Scheduled(fixedDelayString = "${app.production.workers.processing-fixed-delay-ms:10000}")
    @Transactional
    public void processPendingJobs() {
        /*
         * Pobieramy najstarsze joby PENDING.
         * Kolejność po createdAt daje prostą kolejkę FIFO.
         */
        var jobs = jobRepository.findAllByStatusOrderByCreatedAtAsc(
                FileProcessingJobStatus.PENDING,
                PageRequest.of(0, 25)
        );

        for (FileProcessingJob job : jobs) {
            /*
             * Oznaczamy job jako RUNNING przed wykonaniem.
             * Dzięki temu widać, że worker zaczął pracę nad zadaniem.
             */
            job.markRunning();

            try {
                /*
                 * Pobieramy aktywny plik.
                 * Jeśli plik został usunięty po utworzeniu joba, job przejdzie w FAILED.
                 */
                FileMetadata file = fileRepository.findByIdAndDeletedAtIsNull(job.getFileId())
                        .orElseThrow(() -> new NoSuchElementException("File not found for processing job"));

                /*
                 * Dispatch po typie joba.
                 * Każdy typ zadania ma oddzielny serwis odpowiedzialny za konkretną logikę.
                 */
                String result = switch (job.getJobType()) {
                    case ANTIVIRUS_SCAN -> runAntivirus(file);

                    case THUMBNAIL_GENERATION -> thumbnailService.generateIfSupported(file);

                    case DEDUPE_REGISTER -> deduplicationService
                            .register(file)
                            .getId()
                            .toString();
                };

                /*
                 * Zapisujemy sukces i wynik techniczny.
                 * Result może być np. opisem skanu, ścieżką miniaturki albo ID bloba.
                 */
                job.markCompleted(result);

            } catch (Exception e) {
                /*
                 * Błąd pojedynczego joba nie zatrzymuje całego workera.
                 * Job zostaje oznaczony jako FAILED z komunikatem błędu.
                 *
                 * W bardziej zaawansowanej wersji warto dodać retry counter,
                 * nextAttemptAt i przenoszenie do DLQ po przekroczeniu limitu prób.
                 */
                job.markFailed(
                        e.getMessage() == null
                                ? e.getClass().getSimpleName()
                                : e.getMessage()
                );
            }
        }
    }

    /**
     * Uruchamia skan antywirusowy dla pliku.
     *
     * Pobiera plik ze storage jako InputStream i przekazuje go do AntivirusService.
     *
     * Jeśli wynik nie jest clean, rzucany jest wyjątek.
     * To powoduje oznaczenie joba jako FAILED.
     *
     * W pełnej produkcyjnej wersji warto dodatkowo oznaczyć plik jako QUARANTINED,
     * żeby zablokować jego download albo sharing.
     */
    private String runAntivirus(FileMetadata file) {
        /*
         * Pobieramy fizyczną zawartość pliku ze storage.
         */
        var result = antivirusService.scan(
                storageService.download(file.getObjectKey())
        );

        /*
         * Plik podejrzany nie powinien przejść processingu jako sukces.
         */
        if (!result.clean()) {
            throw new IllegalStateException(
                    "Antivirus found suspicious file: " + result.details()
            );
        }

        /*
         * Zwracamy szczegóły skanu, np. "clean" albo nazwę silnika/wynik.
         */
        return result.details();
    }
}
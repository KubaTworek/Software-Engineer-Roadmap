package com.example.filestorage.production.processing;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Serwis odpowiedzialny za tworzenie zadań przetwarzania pliku w tle.
 *
 * Po uploadzie plik nie powinien wykonywać wszystkich ciężkich operacji synchronicznie
 * w request-response.
 *
 * Zamiast tego tworzymy zadania, które później mogą zostać obsłużone przez workerów:
 * - skan antywirusowy,
 * - generowanie miniaturki,
 * - rejestracja pliku do deduplikacji.
 *
 * Dzięki temu upload kończy się szybciej, a kosztowne operacje są wykonywane asynchronicznie.
 */
@Service
public class FileProcessingService {

    /**
     * Repozytorium zadań przetwarzania plików.
     *
     * Przechowuje informacje typu:
     * - fileId,
     * - jobType,
     * - status,
     * - liczba prób,
     * - błąd przetwarzania,
     * - timestamps.
     *
     * Workerzy będą później pobierać z tej tabeli zadania do wykonania.
     */
    private final FileProcessingJobRepository repository;

    public FileProcessingService(FileProcessingJobRepository repository) {
        this.repository = repository;
    }

    /**
     * Dodaje domyślny zestaw zadań dla nowo przesłanego pliku.
     *
     * Ta metoda powinna być wołana po utworzeniu finalnego pliku,
     * np. po uploadzie klasycznym albo po zakończeniu chunked uploadu.
     *
     * Domyślne joby:
     * - ANTIVIRUS_SCAN: sprawdzenie pliku pod kątem malware,
     * - THUMBNAIL_GENERATION: wygenerowanie miniaturki, jeśli typ pliku to obsługuje,
     * - DEDUPE_REGISTER: zapisanie hasha/bloba do mechanizmu deduplikacji.
     *
     * Każde zadanie jest dodawane osobno, żeby worker mógł je przetwarzać niezależnie.
     */
    @Transactional
    public void enqueueDefaultJobs(UUID fileId) {
        enqueue(fileId, FileProcessingJobType.ANTIVIRUS_SCAN);
        enqueue(fileId, FileProcessingJobType.THUMBNAIL_GENERATION);
        enqueue(fileId, FileProcessingJobType.DEDUPE_REGISTER);
    }

    /**
     * Dodaje pojedyncze zadanie przetwarzania dla pliku.
     *
     * Metoda jest idempotentna:
     * jeśli zadanie danego typu dla tego pliku już istnieje,
     * zwracamy istniejący rekord zamiast tworzyć duplikat.
     *
     * To ważne przy retry, ponownym publikowaniu eventów albo wielokrotnym wywołaniu
     * enqueueDefaultJobs dla tego samego pliku.
     */
    @Transactional
    public FileProcessingJob enqueue(UUID fileId, FileProcessingJobType type) {
        return repository.findByFileIdAndJobType(fileId, type)
                .orElseGet(() -> repository.save(
                        new FileProcessingJob(fileId, type)
                ));
    }
}
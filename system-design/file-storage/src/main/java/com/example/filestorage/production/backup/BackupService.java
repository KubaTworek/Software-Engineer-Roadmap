package com.example.filestorage.production.backup;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Serwis odpowiedzialny za operacje backupowe.
 *
 * Aktualna implementacja tworzy tylko manifest backupu metadanych.
 * To jest techniczny zapis informujący, że backup powinien obejmować:
 * - bazę danych, np. przez pg_dump,
 * - object storage, np. przez replikację MinIO/S3,
 * - procedurę odtworzeniową.
 *
 * Ta klasa nie robi pełnego backupu produkcyjnego.
 * Nie eksportuje bazy danych i nie kopiuje obiektów z object storage.
 *
 * W produkcji BackupService powinien być raczej koordynatorem procesu,
 * a właściwy backup powinien być wykonywany przez narzędzia infrastrukturalne.
 */
@Service
public class BackupService {

    /**
     * Repozytorium historii backupów.
     *
     * Każde uruchomienie backupu/manifestu zapisuje BackupRun,
     * żeby operator mógł sprawdzić status, ścieżkę wyniku i komunikat.
     */
    private final BackupRunRepository repository;

    /**
     * Konfiguracja backupu.
     *
     * W tej implementacji zawiera lokalny katalog,
     * do którego zostanie zapisany manifest.
     */
    private final BackupProperties properties;

    public BackupService(BackupRunRepository repository,
                         BackupProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * Tworzy lokalny manifest backupu metadanych.
     *
     * Flow:
     * - zapisuje BackupRun w bazie,
     * - tworzy katalog backupów,
     * - zapisuje plik manifestu .txt,
     * - oznacza run jako zakończony sukcesem albo błędem,
     * - zapisuje końcowy stan BackupRun.
     *
     * Manifest jest bardziej instrukcją/śladem operacyjnym niż realnym backupem.
     */
    @Transactional
    public BackupRun createMetadataBackupManifest() {
        /*
         * Tworzymy rekord uruchomienia backupu.
         *
         * Konstruktor BackupRun prawdopodobnie ustawia:
         * - typ backupu,
         * - status RUNNING,
         * - startedAt.
         */
        BackupRun run = repository.save(
                new BackupRun("METADATA_MANIFEST")
        );

        try {
            /*
             * Lokalny katalog na manifesty.
             *
             * W produkcji lokalny filesystem kontenera jest zwykle nietrwały,
             * więc taki manifest powinien trafiać raczej do trwałego storage.
             */
            Path dir = Path.of(properties.localDir());

            /*
             * Tworzy katalog, jeśli jeszcze nie istnieje.
             */
            Files.createDirectories(dir);

            /*
             * Nazwa pliku zawiera timestamp.
             *
             * Dwukropki są zamieniane na myślniki, bo na części systemów plików
             * dwukropek bywa problematyczny albo niepożądany w nazwach.
             */
            Path manifest = dir.resolve(
                    "backup-manifest-"
                            + Instant.now().toString().replace(':', '-')
                            + ".txt"
            );

            /*
             * Zapisujemy prosty tekstowy manifest.
             *
             * Ważne: to nie jest dump bazy ani kopia obiektów.
             * To tylko informacja dla operatora, co powinno być wykonane
             * dla realnego odtworzenia systemu.
             */
            Files.writeString(
                    manifest,
                    "File Storage metadata backup manifest\n"
                            + "Run: " + run.getId() + "\n"
                            + "Created at: " + Instant.now() + "\n"
                            + "Use pg_dump and object-storage replication for production restore.\n"
            );

            /*
             * Oznaczamy BackupRun jako zakończony sukcesem
             * i zapisujemy ścieżkę do manifestu.
             */
            run.finish(
                    manifest.toString(),
                    "Manifest created. Production backups should run pg_dump plus MinIO/S3 versioned replication."
            );

        } catch (Exception e) {
            /*
             * Jeśli cokolwiek pójdzie źle, zapisujemy status błędu w BackupRun.
             *
             * Nie rzucamy wyjątku dalej, bo chcemy zwrócić operatorowi rekord runa
             * z informacją o niepowodzeniu.
             */
            run.fail(
                    e.getMessage() == null
                            ? e.getClass().getSimpleName()
                            : e.getMessage()
            );
        }

        /*
         * Zapis końcowego stanu:
         * - COMPLETED z lokalizacją manifestu,
         * - albo FAILED z komunikatem błędu.
         */
        return repository.save(run);
    }
}
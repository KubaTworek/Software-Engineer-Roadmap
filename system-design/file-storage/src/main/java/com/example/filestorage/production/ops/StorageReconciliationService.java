package com.example.filestorage.production.ops;

import com.example.filestorage.file.FileMetadataRepository;
import com.example.filestorage.storage.StorageService;
import com.example.filestorage.version.FileVersionRepository;
import com.example.filestorage.upload.UploadChunkRepository;
import com.example.filestorage.production.processing.FileProcessingJobRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;

/**
 * Porownuje zrodlo prawdy metadanych z object storage. Celowo tylko raportuje:
 * automatyczne usuniecie "orphan" moze skasowac obiekt utworzony chwile przed
 * commitem bazy. Naprawa wymaga okresu karencji i osobnej decyzji operatora.
 */
@Service
public class StorageReconciliationService {
    private final FileMetadataRepository files;
    private final StorageConsistencyFindingRepository findings;
    private final StorageService storage;
    private final Clock clock;
    private final FileVersionRepository versions;
    private final UploadChunkRepository chunks;
    private final FileProcessingJobRepository processingJobs;

    public StorageReconciliationService(FileMetadataRepository files,
                                        StorageConsistencyFindingRepository findings,
                                        StorageService storage,
                                        Clock clock,
                                        FileVersionRepository versions,
                                        UploadChunkRepository chunks,
                                        FileProcessingJobRepository processingJobs) {
        this.files = files;
        this.findings = findings;
        this.storage = storage;
        this.clock = clock;
        this.versions = versions;
        this.chunks = chunks;
        this.processingJobs = processingJobs;
    }

    /**
     * Kierunek storage -> DB wykrywa osierocone obiekty. Obiekt mlodszy od
     * grace period moze nalezec do trwajacej sagi, dlatego nie jest findingiem.
     * Metoda raportuje, ale nigdy automatycznie nie kasuje danych.
     */
    @Transactional
    public OrphanScanResult checkStorageInventory(String prefix, int requestedLimit, Duration gracePeriod) {
        int limit = Math.min(Math.max(requestedLimit, 1), 10_000);
        var referenced = new HashSet<>(versions.findAllReferencedObjectKeys());
        referenced.addAll(chunks.findAllReferencedObjectKeys());
        referenced.addAll(processingJobs.findAllResultObjectKeys());
        var cutoff = clock.instant().minus(gracePeriod.isNegative() ? Duration.ZERO : gracePeriod);
        int orphans = 0;
        var inventory = storage.listObjects(prefix == null ? "" : prefix, limit);
        for (var object : inventory) {
            if (object.lastModified().isBefore(cutoff) && !referenced.contains(object.key())) {
                findings.save(new StorageConsistencyFinding(
                        "ORPHAN_OBJECT", object.key(), null,
                        "Object has no file version, upload chunk or processing result reference",
                        clock.instant()));
                orphans++;
            }
        }
        return new OrphanScanResult(inventory.size(), referenced.size(), orphans);
    }

    @Transactional
    public ReconciliationResult checkMetadataReferences(int requestedLimit) {
        int limit = Math.min(Math.max(requestedLimit, 1), 1_000);
        var sample = files.findAll(PageRequest.of(0, limit)).getContent();
        int missing = 0;
        for (var file : sample) {
            if (!storage.exists(file.getObjectKey())) {
                findings.save(new StorageConsistencyFinding(
                        "MISSING_OBJECT", file.getObjectKey(), file.getId(),
                        "Metadata points to an object that storage cannot find", clock.instant()));
                missing++;
            }
        }
        return new ReconciliationResult(sample.size(), missing);
    }

    public record ReconciliationResult(int checkedReferences, int missingObjects) {}
    public record OrphanScanResult(int checkedObjects, int knownReferences, int orphanObjects) {}
}

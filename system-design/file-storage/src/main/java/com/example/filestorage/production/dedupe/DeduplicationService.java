package com.example.filestorage.production.dedupe;

import com.example.filestorage.file.FileMetadata;
import com.example.filestorage.storage.StorageService;
import com.example.filestorage.version.FileVersionRepository;
import com.example.filestorage.version.FileVersion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Content-addressed registry dla fizycznych blobów. Deduplikacja następuje
 * dopiero po pełnej weryfikacji hasha; API nigdy nie ujawnia klientowi, czy
 * identyczny hash należał wcześniej do innego użytkownika.
 */
@Service
public class DeduplicationService {
    private final StorageBlobRepository blobs;
    private final FileVersionRepository versions;
    private final StorageService storage;

    public DeduplicationService(StorageBlobRepository blobs,
                                FileVersionRepository versions,
                                StorageService storage) {
        this.blobs = blobs;
        this.versions = versions;
        this.storage = storage;
    }

    @Transactional
    public UUID registerVersion(FileMetadata file) {
        var version = versions.findById(file.getCurrentVersionId())
                .orElseThrow(() -> new NoSuchElementException("Current file version not found"));
        if (version.getBlobId() != null) {
            return version.getBlobId();
        }

        String uploadedObject = version.getObjectKey();
        blobs.lockHash(file.getSha256());
        StorageBlob blob = blobs.findBySha256ForUpdate(file.getSha256())
                .map(existing -> {
                    if (existing.getSizeBytes() != file.getSizeBytes()) {
                        throw new IllegalStateException("Identical SHA-256 has a different size");
                    }
                    existing.incrementRefCount();
                    return existing;
                })
                .orElseGet(() -> blobs.save(new StorageBlob(
                        file.getSha256(), uploadedObject, file.getSizeBytes(), null)));

        version.attachBlob(blob.getId(), blob.getObjectKey());
        file.useCanonicalBlob(blob.getObjectKey());

        if (!uploadedObject.equals(blob.getObjectKey())) {
            afterCommit(() -> storage.delete(uploadedObject));
        }
        return blob.getId();
    }

    /**
     * Zwalnia jedna logiczna referencje do zawartosci. Obiekt jest usuwany
     * dopiero po zatwierdzeniu transakcji i tylko gdy licznik doszedl do zera.
     * Dzieki temu rollback bazy nie pozostawia metadanych wskazujacych na
     * fizycznie skasowany, wspoldzielony blob.
     */
    @Transactional
    public void releaseVersion(FileVersion version) {
        if (version.getBlobId() == null) {
            afterCommit(() -> storage.delete(version.getObjectKey()));
            return;
        }

        StorageBlob blob = blobs.findByIdForUpdate(version.getBlobId())
                .orElseThrow(() -> new IllegalStateException("Referenced storage blob does not exist"));
        if (blob.decrementRefCount() == 0) {
            String objectKey = blob.getObjectKey();
            blobs.delete(blob);
            afterCommit(() -> storage.delete(objectKey));
        }
    }

    private void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }
}

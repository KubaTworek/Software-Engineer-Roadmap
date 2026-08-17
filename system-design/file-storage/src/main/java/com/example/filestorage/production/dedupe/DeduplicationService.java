package com.example.filestorage.production.dedupe;

import com.example.filestorage.file.FileMetadata;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serwis odpowiedzialny za rejestrację plików w mechanizmie deduplikacji.
 *
 * Deduplikacja polega na tym, że wiele logicznych plików może wskazywać
 * na tę samą fizyczną zawartość, jeżeli mają identyczny hash SHA-256.
 *
 * Ta klasa nie przenosi jeszcze plików ani nie zmienia objectKey w FileMetadata.
 * Aktualnie pełni rolę rejestru blobów:
 * - zapisuje nowy blob, jeśli hash nie istnieje,
 * - zwiększa refCount, jeśli blob o tym SHA-256 już istnieje.
 */
@Service
public class DeduplicationService {

    /**
     * Repozytorium fizycznych blobów w storage.
     *
     * StorageBlob opisuje zawartość pliku niezależnie od logicznego FileMetadata:
     * - sha256,
     * - objectKey,
     * - sizeBytes,
     * - refCount,
     * - opcjonalnie informacje o szyfrowaniu albo storage class.
     */
    private final StorageBlobRepository blobRepository;

    public DeduplicationService(StorageBlobRepository blobRepository) {
        this.blobRepository = blobRepository;
    }

    /**
     * Rejestruje plik jako blob storage.
     *
     * Używane zwykle przez background processing po uploadzie pliku.
     *
     * Działanie:
     * - szuka istniejącego StorageBlob po SHA-256 pliku,
     * - jeśli istnieje, zwiększa jego refCount,
     * - jeśli nie istnieje, tworzy nowy StorageBlob z objectKey pliku.
     *
     * Metoda jest transakcyjna, bo zmiana refCount albo utworzenie bloba
     * musi być spójna z zapisem w bazie.
     */
    @Transactional
    public StorageBlob register(FileMetadata file) {
        return blobRepository.findBySha256(file.getSha256())
                .map(existing -> {
                    /*
                     * Taka sama zawartość już istnieje w systemie.
                     *
                     * Zwiększamy licznik referencji, bo kolejny logiczny plik
                     * wskazuje na ten sam hash.
                     *
                     * Uwaga: ta implementacja nie przepina file.objectKey
                     * na istniejący blob. Ona tylko rejestruje duplikat.
                     */
                    existing.incrementRefCount();

                    return existing;
                })
                .orElseGet(() -> blobRepository.save(
                        /*
                         * Pierwszy raz widzimy taką zawartość.
                         * Tworzymy nowy rekord StorageBlob.
                         *
                         * Ostatni argument null może oznaczać np. brak informacji
                         * o klasie storage, kluczu szyfrowania albo dodatkowych metadanych,
                         * zależnie od konstrukcji StorageBlob.
                         */
                        new StorageBlob(
                                file.getSha256(),
                                file.getObjectKey(),
                                file.getSizeBytes(),
                                null
                        )
                ));
    }
}
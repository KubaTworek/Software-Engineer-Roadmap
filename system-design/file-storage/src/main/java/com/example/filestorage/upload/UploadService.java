package com.example.filestorage.upload;

import com.example.filestorage.auth.AppUser;
import com.example.filestorage.auth.UserRepository;
import com.example.filestorage.file.FileMetadata;
import com.example.filestorage.file.FileMetadataRepository;
import com.example.filestorage.file.FileResponse;
import com.example.filestorage.version.FileVersion;
import com.example.filestorage.version.FileVersionRepository;
import com.example.filestorage.sync.ChangeLogService;
import com.example.filestorage.search.SearchIndexService;
import com.example.filestorage.sharing.ResourceType;
import com.example.filestorage.folder.FolderService;
import com.example.filestorage.storage.StorageService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Serwis biznesowy odpowiedzialny za stabilny upload dużych plików.
 *
 * Obsługuje flow:
 * 1. Utworzenie upload session.
 * 2. Wygenerowanie presigned URL dla każdego chunka.
 * 3. Upload chunków bezpośrednio do object storage.
 * 4. Potwierdzanie chunków przez backend.
 * 5. Finalizację uploadu i złożenie finalnego pliku.
 * 6. Cleanup tymczasowych chunków.
 *
 * Dzięki temu duże pliki nie przechodzą przez backend aplikacyjny.
 * Backend kontroluje proces, ale bajty pliku trafiają bezpośrednio do storage.
 */
@Service
public class UploadService {

    /**
     * Minimalny rozmiar chunka dla uploadu wieloczęściowego.
     *
     * 5 MiB to typowy minimalny rozmiar części w storage typu S3/MinIO
     * przy operacjach multipart/compose.
     */
    private static final long MIN_CHUNK_SIZE = 5L * 1024L * 1024L;

    /**
     * Maksymalny rozmiar chunka.
     *
     * Ogranicza koszt retry oraz ryzyko bardzo dużych transferów pojedynczej części.
     */
    private static final long MAX_CHUNK_SIZE = 128L * 1024L * 1024L;

    /**
     * Czas życia sesji uploadu.
     *
     * Po tym czasie niedokończony upload może zostać automatycznie wyczyszczony.
     */
    private static final Duration SESSION_TTL = Duration.ofHours(24);

    /**
     * Czas ważności presigned URL.
     *
     * URL powinien być krótko ważny, bo daje bezpośrednie prawo zapisu do storage.
     */
    private static final Duration PRESIGNED_URL_TTL = Duration.ofMinutes(15);

    /**
     * Repozytorium sesji uploadu.
     * Przechowuje stan całego uploadu: status, totalSize, chunkSize, totalChunks, expiresAt.
     */
    private final UploadSessionRepository uploadSessionRepository;

    /**
     * Repozytorium potwierdzonych chunków.
     * Każdy rekord oznacza, że konkretny chunk został wysłany i zweryfikowany.
     */
    private final UploadChunkRepository uploadChunkRepository;

    /**
     * Repozytorium metadanych plików.
     * Używane dopiero przy finalizacji, gdy upload staje się normalnym plikiem w systemie.
     */
    private final FileMetadataRepository fileMetadataRepository;

    /**
     * Repozytorium użytkowników.
     * Potrzebne do blokady użytkownika i aktualizacji quota przy finalizacji.
     */
    private final UserRepository userRepository;

    /**
     * Serwis folderów.
     * Waliduje folder docelowy i sprawdza konflikt nazw.
     */
    private final FolderService folderService;

    /**
     * Abstrakcja nad object storage.
     * Generuje presigned URL-e, sprawdza rozmiary chunków, składa obiekty i usuwa artefakty.
     */
    private final StorageService storageService;

    /**
     * Repozytorium wersji plików.
     * Finalizacja uploadu tworzy pierwszą wersję nowego pliku.
     */
    private final FileVersionRepository fileVersionRepository;

    /**
     * Changelog do synchronizacji klientów.
     */
    private final ChangeLogService changeLogService;

    /**
     * Asynchroniczny indeks wyszukiwania.
     */
    private final SearchIndexService searchIndexService;
    private final UploadFinalizationCoordinator finalizationCoordinator;
    private final Clock clock;

    public UploadService(UploadSessionRepository uploadSessionRepository,
                         UploadChunkRepository uploadChunkRepository,
                         FileMetadataRepository fileMetadataRepository,
                         UserRepository userRepository,
                         FolderService folderService,
                         StorageService storageService,
                         FileVersionRepository fileVersionRepository,
                         ChangeLogService changeLogService,
                         SearchIndexService searchIndexService,
                         UploadFinalizationCoordinator finalizationCoordinator,
                         Clock clock) {
        this.uploadSessionRepository = uploadSessionRepository;
        this.uploadChunkRepository = uploadChunkRepository;
        this.fileMetadataRepository = fileMetadataRepository;
        this.userRepository = userRepository;
        this.folderService = folderService;
        this.storageService = storageService;
        this.fileVersionRepository = fileVersionRepository;
        this.changeLogService = changeLogService;
        this.searchIndexService = searchIndexService;
        this.finalizationCoordinator = finalizationCoordinator;
        this.clock = clock;
    }

    /**
     * Inicjuje upload dużego pliku.
     *
     * Na tym etapie nie zapisujemy jeszcze pliku w docelowej tabeli files.
     * Tworzymy tylko upload session, która opisuje plan uploadu.
     *
     * Backend waliduje:
     * - rozmiar całkowity pliku,
     * - rozmiar chunka,
     * - nazwę pliku,
     * - folder docelowy,
     * - konflikt nazwy w folderze.
     */
    @Transactional
    public InitiateUploadResponse initiate(UUID userId, InitiateUploadRequest request) {
        /*
         * Nie ma sensu tworzyć sesji dla pustego albo ujemnego rozmiaru.
         */
        if (request.totalSizeBytes() <= 0) {
            throw new IllegalArgumentException("totalSizeBytes must be positive");
        }

        /*
         * Jeśli plik wymaga wielu chunków, chunk nie może być zbyt mały.
         * Małe chunki zwiększają liczbę requestów, rekordów w DB i koszt compose.
         */
        if (request.chunkSizeBytes() < MIN_CHUNK_SIZE && request.totalSizeBytes() > request.chunkSizeBytes()) {
            throw new IllegalArgumentException("chunkSizeBytes must be at least 5 MiB for multi-chunk uploads");
        }

        /*
         * Zbyt duży chunk pogarsza retry.
         * Jeśli upload chunka padnie, klient musi powtórzyć większy transfer.
         */
        if (request.chunkSizeBytes() > MAX_CHUNK_SIZE) {
            throw new IllegalArgumentException("chunkSizeBytes must be at most 128 MiB");
        }

        String filename = sanitizeFilename(request.filename());

        String contentType = request.contentType() == null || request.contentType().isBlank()
                ? "application/octet-stream"
                : request.contentType();

        /*
         * Folder docelowy musi istnieć i należeć do użytkownika.
         * W tej metodzie nie ma pełnej obsługi folderów współdzielonych.
         */
        folderService.validateParentFolder(userId, request.parentFolderId());

        /*
         * Już na starcie blokujemy oczywisty konflikt nazwy.
         * Finalizacja sprawdza to ponownie, bo w międzyczasie ktoś mógł utworzyć plik o tej nazwie.
         */
        folderService.ensureFileNameAvailable(userId, request.parentFolderId(), filename);

        /*
         * Tymczasowe chunki również zużywają realny storage. Rezerwujemy quota
         * przed wydaniem pierwszego presigned URL, a nie dopiero przy complete.
         */
        AppUser user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        user.reserveUpload(request.totalSizeBytes());
        userRepository.save(user);

        /*
         * Liczba chunków liczona z zaokrągleniem w górę.
         */
        int totalChunks = Math.toIntExact(
                (request.totalSizeBytes() + request.chunkSizeBytes() - 1)
                        / request.chunkSizeBytes()
        );

        UploadSession session = new UploadSession(
                userId,
                request.parentFolderId(),
                filename,
                contentType,
                request.totalSizeBytes(),
                request.chunkSizeBytes(),
                totalChunks,
                normalizeHash(request.expectedSha256()),
                clock.instant().plus(SESSION_TTL)
        );

        UploadSession saved = uploadSessionRepository.save(session);

        return new InitiateUploadResponse(
                saved.getId(),
                saved.getFilename(),
                saved.getTotalSizeBytes(),
                saved.getChunkSizeBytes(),
                saved.getTotalChunks(),
                saved.getExpiresAt()
        );
    }

    /**
     * Zwraca aktualny stan sesji uploadu.
     *
     * To jest podstawowy mechanizm resumable upload.
     * Klient po restarcie aplikacji albo zerwaniu sieci może pobrać listę
     * już potwierdzonych chunków i wysłać tylko brakujące.
     */
    @Transactional(readOnly = true)
    public UploadSessionResponse getSession(UUID userId, UUID uploadId) {
        UploadSession session = findOwnedSession(userId, uploadId);

        return UploadSessionResponse.from(
                session,
                uploadChunkRepository.findAllByUploadSessionIdOrderByChunkIndexAsc(uploadId)
        );
    }

    /**
     * Generuje presigned PUT URL dla konkretnego chunka.
     *
     * Backend nie odbiera tutaj danych pliku.
     * Zwraca tylko tymczasowy URL, pod który klient wykona PUT bezpośrednio do storage.
     */
    @Transactional(readOnly = true)
    public PresignedChunkUrlResponse presignedUrl(UUID userId, UUID uploadId, int chunkIndex) {
        UploadSession session = findOwnedSession(userId, uploadId);

        /*
         * Sesja musi być aktywna, niewygasła i w statusie umożliwiającym zapis.
         */
        validateSessionWritable(session);

        /*
         * Chunk index musi mieścić się w zakresie 0..totalChunks-1.
         */
        validateChunkIndex(session, chunkIndex);

        /*
         * Każdy chunk ma deterministyczny objectKey.
         * Dzięki temu retry tego samego chunka trafia w to samo miejsce.
         */
        String objectKey = chunkObjectKey(session, chunkIndex);

        String url = storageService.presignedPutUrl(objectKey, PRESIGNED_URL_TTL);

        return new PresignedChunkUrlResponse(
                uploadId,
                chunkIndex,
                objectKey,
                url,
                "PUT",
                clock.instant().plus(PRESIGNED_URL_TTL)
        );
    }

    /**
     * Potwierdza zakończenie uploadu pojedynczego chunka.
     *
     * Klient wywołuje tę metodę po udanym PUT na presigned URL.
     *
     * Backend nie ufa klientowi bezwarunkowo:
     * - sprawdza rzeczywisty rozmiar obiektu w storage,
     * - porównuje go z oczekiwanym rozmiarem chunka,
     * - liczy SHA-256 z obiektu w storage,
     * - porównuje z SHA-256 przesłanym przez klienta,
     * - zapisuje chunk jako ukończony.
     */
    @Transactional
    public ChunkResponse completeChunk(UUID userId,
                                       UUID uploadId,
                                       int chunkIndex,
                                       CompleteChunkRequest request) {
        /*
         * FOR UPDATE na sesji chroni licznik uploadedChunks i status
         * przed równoległym potwierdzaniem chunków.
         */
        UploadSession session = uploadSessionRepository.findByIdAndUserIdForUpdate(uploadId, userId)
                .orElseThrow(() -> new NoSuchElementException("Upload session not found"));

        validateSessionWritable(session);
        validateChunkIndex(session, chunkIndex);

        String objectKey = chunkObjectKey(session, chunkIndex);

        /*
         * Rzeczywisty rozmiar chunka w object storage.
         */
        long actualSize = storageService.size(objectKey);

        /*
         * Oczekiwany rozmiar chunka.
         * Ostatni chunk zwykle jest mniejszy niż chunkSizeBytes.
         */
        long expectedSize = expectedChunkSize(session, chunkIndex);

        if (actualSize != expectedSize) {
            throw new IllegalArgumentException(
                    "Chunk size mismatch. Expected %d bytes but got %d bytes".formatted(expectedSize, actualSize)
            );
        }

        /*
         * Klient również raportuje rozmiar.
         * Musi zgadzać się z tym, co widzi storage.
         */
        if (request.sizeBytes() != actualSize) {
            throw new IllegalArgumentException("Reported chunk size does not match object storage size");
        }

        /*
         * Liczymy hash z faktycznego obiektu w storage.
         * To kosztowne dla dużych chunków, ale daje realną walidację integralności.
         */
        String actualSha256 = sha256(storageService.download(objectKey));
        String reportedSha256 = normalizeHash(request.sha256());

        if (!actualSha256.equals(reportedSha256)) {
            throw new IllegalArgumentException("Chunk SHA-256 mismatch");
        }

        /*
         * Idempotencja retry:
         * jeśli chunk został już potwierdzony z tym samym hashem i rozmiarem,
         * zwracamy istniejący rekord zamiast tworzyć duplikat.
         */
        return uploadChunkRepository.findByUploadSessionIdAndChunkIndex(uploadId, chunkIndex)
                .map(existing -> {
                    if (!existing.getSha256().equals(actualSha256) || existing.getSizeBytes() != actualSize) {
                        throw new IllegalArgumentException("Chunk was already completed with different metadata");
                    }

                    return ChunkResponse.from(existing);
                })
                .orElseGet(() -> {
                    UploadChunk saved = uploadChunkRepository.save(
                            new UploadChunk(
                                    uploadId,
                                    chunkIndex,
                                    objectKey,
                                    actualSha256,
                                    actualSize
                            )
                    );

                    /*
                     * Licznik potwierdzonych chunków jest trzymany na sesji,
                     * żeby szybko pokazać postęp uploadu.
                     */
                    session.markChunkUploaded();
                    uploadSessionRepository.save(session);

                    return ChunkResponse.from(saved);
                });
    }

    /**
     * Finalizuje upload i tworzy właściwy plik w systemie.
     *
     * Dopiero tutaj:
     * - sprawdzamy kompletność chunków,
     * - rezerwujemy quota,
     * - składamy finalny obiekt,
     * - tworzymy FileMetadata,
     * - tworzymy pierwszą FileVersion,
     * - zapisujemy changelog,
     * - aktualizujemy indeks wyszukiwania,
     * - usuwamy tymczasowe chunki.
     */
    public FileResponse completeUpload(UUID userId, UUID uploadId) {
        return finalizationCoordinator.complete(userId, uploadId);
    }

    /**
     * Anuluje niedokończony upload.
     *
     * Usuwa tymczasowe chunki i oznacza sesję jako ABORTED.
     */
    @Transactional
    public void abort(UUID userId, UUID uploadId) {
        UploadSession session = uploadSessionRepository.findByIdAndUserIdForUpdate(uploadId, userId)
                .orElseThrow(() -> new NoSuchElementException("Upload session not found"));

        if (session.getStatus() == UploadStatus.COMPLETED) {
            throw new IllegalStateException("Completed upload cannot be aborted");
        }

        List<UploadChunk> chunks = uploadChunkRepository.findAllByUploadSessionIdOrderByChunkIndexAsc(uploadId);

        /*
         * Usuwamy tylko tymczasowe chunki.
         * Nie ma finalnego pliku, więc nie ma metadanych FileMetadata ani FileVersion.
         */
        chunks.forEach(chunk -> storageService.delete(chunk.getObjectKey()));
        uploadChunkRepository.deleteAllByUploadSessionId(uploadId);

        session.markAborted();
        uploadSessionRepository.save(session);

        userRepository.findByIdForUpdate(userId).ifPresent(user -> {
            user.releaseReservedStorage(session.getTotalSizeBytes());
            userRepository.save(user);
        });
    }

    /**
     * Automatyczny cleanup wygasłych sesji uploadu.
     *
     * Uruchamiany cyklicznie przez scheduler.
     *
     * Czyści:
     * - sesje INITIATED,
     * - sesje IN_PROGRESS,
     * - sesje FAILED,
     * których expiresAt jest w przeszłości.
     *
     * Nie czyści sesji COMPLETED ani ABORTED.
     */
    @Scheduled(fixedDelayString = "${app.upload.cleanup-fixed-delay-ms:900000}")
    @Transactional
    public void cleanupExpiredSessions() {
        List<UploadSession> expired = uploadSessionRepository.findTop100ByStatusInAndExpiresAtBefore(
                List.of(
                        UploadStatus.INITIATED,
                        UploadStatus.IN_PROGRESS,
                        UploadStatus.FAILED
                ),
                clock.instant()
        );

        for (UploadSession session : expired) {
            List<UploadChunk> chunks =
                    uploadChunkRepository.findAllByUploadSessionIdOrderByChunkIndexAsc(session.getId());

            /*
             * Usuwamy wszystkie tymczasowe chunki powiązane z wygasłą sesją.
             */
            chunks.forEach(chunk -> storageService.delete(chunk.getObjectKey()));
            uploadChunkRepository.deleteAllByUploadSessionId(session.getId());

            /*
             * Sesję oznaczamy jako EXPIRED, żeby było wiadomo,
             * że została zakończona przez cleanup, nie przez użytkownika.
             */
            session.markExpired();
            uploadSessionRepository.save(session);

            userRepository.findByIdForUpdate(session.getUserId()).ifPresent(user -> {
                user.releaseReservedStorage(session.getTotalSizeBytes());
                userRepository.save(user);
            });
        }
    }

    /**
     * Pobiera sesję należącą do użytkownika.
     *
     * Dzięki filtrowaniu po userId użytkownik nie może podejrzeć
     * ani kontrolować cudzych uploadów.
     */
    private UploadSession findOwnedSession(UUID userId, UUID uploadId) {
        return uploadSessionRepository.findByIdAndUserId(uploadId, userId)
                .orElseThrow(() -> new NoSuchElementException("Upload session not found"));
    }

    /**
     * Sprawdza, czy sesja nadal może przyjmować chunki lub zostać sfinalizowana.
     *
     * Typowo wyklucza sesje:
     * - COMPLETED,
     * - ABORTED,
     * - EXPIRED,
     * - wygasłe czasowo.
     */
    private void validateSessionWritable(UploadSession session) {
        if (!session.isWritable(clock.instant())) {
            throw new IllegalStateException("Upload session is not writable or has expired");
        }
    }

    /**
     * Waliduje indeks chunka.
     *
     * Poprawny zakres to:
     * 0 <= chunkIndex < totalChunks.
     */
    private void validateChunkIndex(UploadSession session, int chunkIndex) {
        if (chunkIndex < 0 || chunkIndex >= session.getTotalChunks()) {
            throw new IllegalArgumentException("chunkIndex out of range");
        }
    }

    /**
     * Wylicza oczekiwany rozmiar konkretnego chunka.
     *
     * Wszystkie chunki poza ostatnim mają pełny chunkSizeBytes.
     * Ostatni chunk może być mniejszy.
     */
    private long expectedChunkSize(UploadSession session, int chunkIndex) {
        if (chunkIndex < session.getTotalChunks() - 1) {
            return session.getChunkSizeBytes();
        }

        long previousChunksSize =
                session.getChunkSizeBytes() * (session.getTotalChunks() - 1L);

        return session.getTotalSizeBytes() - previousChunksSize;
    }

    /**
     * Buduje techniczny objectKey dla chunka.
     *
     * Deterministyczny klucz pozwala bezpiecznie ponowić upload tego samego chunka.
     */
    private String chunkObjectKey(UploadSession session, int chunkIndex) {
        return "users/%s/uploads/%s/chunks/%d".formatted(
                session.getUserId(),
                session.getId(),
                chunkIndex
        );
    }

    /**
     * Liczy SHA-256 ze strumienia.
     *
     * Używane do walidacji:
     * - pojedynczych chunków,
     * - finalnego złożonego pliku.
     *
     * Strumień jest zamykany przez try-with-resources.
     */
    private String sha256(InputStream inputStream) {
        try (InputStream in = inputStream) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] buffer = new byte[8192];
            int read;

            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }

            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IllegalStateException("Could not calculate SHA-256", e);
        }
    }

    /**
     * Normalizuje hash do lowercase.
     *
     * Dzięki temu porównanie hashy nie zależy od wielkości liter.
     */
    private String normalizeHash(String hash) {
        return hash == null || hash.isBlank()
                ? null
                : hash.toLowerCase();
    }

    /**
     * Czyści nazwę pliku.
     *
     * Usuwa znaki problematyczne dla nagłówków HTTP, logów i objectKey.
     */
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "file";
        }

        String sanitized = filename
                .replaceAll("[\\r\\n\\t]", "_")
                .replaceAll("[/\\\\]", "_")
                .trim();

        if (sanitized.length() > 255) {
            sanitized = sanitized.substring(0, 255);
        }

        return sanitized.isBlank() ? "file" : sanitized;
    }
}

package com.example.filestorage.version;

import com.example.filestorage.audit.AuditService;
import com.example.filestorage.auth.AppUser;
import com.example.filestorage.auth.UserRepository;
import com.example.filestorage.file.FileMetadata;
import com.example.filestorage.file.FileMetadataRepository;
import com.example.filestorage.file.FileResponse;
import com.example.filestorage.search.SearchIndexService;
import com.example.filestorage.sharing.AccessControlService;
import com.example.filestorage.sharing.PermissionRole;
import com.example.filestorage.sharing.ResourceType;
import com.example.filestorage.storage.StorageService;
import com.example.filestorage.sync.ChangeLogService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Serwis biznesowy odpowiedzialny za wersjonowanie plików.
 *
 * Obsługuje:
 * - listowanie historii wersji,
 * - pobieranie konkretnej wersji,
 * - upload nowej wersji,
 * - wykrywanie konfliktów edycji przez baseVersionId,
 * - tworzenie conflict copy,
 * - przywracanie starszej wersji jako aktualnej,
 * - aktualizację quota,
 * - zapis do storage,
 * - audit log,
 * - changelog,
 * - reindeksację wyszukiwania.
 */
@Service
public class FileVersionService {

    /**
     * Repozytorium wersji plików.
     * Każdy rekord reprezentuje jedną fizyczną wersję pliku w storage.
     */
    private final FileVersionRepository versionRepository;

    /**
     * Repozytorium metadanych plików.
     * FileMetadata trzyma aktualny stan pliku, w tym currentVersionId/currentVersionNumber.
     */
    private final FileMetadataRepository fileRepository;

    /**
     * Repozytorium użytkowników.
     * Potrzebne do blokowania ownera i aktualizacji quota przy dodaniu nowej wersji.
     */
    private final UserRepository userRepository;

    /**
     * Serwis object storage.
     * Odpowiada za upload i download binarnej zawartości wersji.
     */
    private final StorageService storageService;

    /**
     * Centralny serwis autoryzacji.
     * Sprawdza, czy użytkownik ma VIEWER albo EDITOR do pliku.
     */
    private final AccessControlService accessControlService;

    /**
     * Serwis audytu.
     * Rejestruje pobranie wersji, utworzenie wersji, restore i conflict copy.
     */
    private final AuditService auditService;

    /**
     * Changelog do synchronizacji klientów.
     * Każda zmiana wersji powinna być widoczna dla klientów synchronizujących pliki.
     */
    private final ChangeLogService changeLogService;

    /**
     * Asynchroniczny indeks wyszukiwania.
     * Po zmianie aktualnej wersji pliku trzeba odświeżyć rekord w indeksie.
     */
    private final SearchIndexService searchIndexService;

    public FileVersionService(FileVersionRepository versionRepository,
                              FileMetadataRepository fileRepository,
                              UserRepository userRepository,
                              StorageService storageService,
                              AccessControlService accessControlService,
                              AuditService auditService,
                              ChangeLogService changeLogService,
                              SearchIndexService searchIndexService) {
        this.versionRepository = versionRepository;
        this.fileRepository = fileRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.accessControlService = accessControlService;
        this.auditService = auditService;
        this.changeLogService = changeLogService;
        this.searchIndexService = searchIndexService;
    }

    /**
     * Listuje wersje wskazanego pliku.
     *
     * Wymaga roli VIEWER, bo historia wersji jest częścią danych pliku.
     *
     * Wyniki są sortowane malejąco po versionNumber,
     * czyli najnowsze wersje są zwracane jako pierwsze.
     */
    @Transactional(readOnly = true)
    public FileVersionListResponse list(UUID actorUserId, UUID fileId, int page, int size) {
        /*
         * Sprawdzenie dostępu do pliku.
         * Bez tego użytkownik mógłby podejrzeć historię cudzych plików.
         */
        accessControlService.requireFileRole(actorUserId, fileId, PermissionRole.VIEWER);

        /*
         * Bezpieczna paginacja.
         * Maksymalnie 100 wersji w jednym requestcie.
         */
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        Page<FileVersion> result = versionRepository.findAllByFileIdOrderByVersionNumberDesc(
                fileId,
                PageRequest.of(
                        safePage,
                        safeSize,
                        Sort.by(Sort.Direction.DESC, "versionNumber")
                )
        );

        return new FileVersionListResponse(
                result.getContent()
                        .stream()
                        .map(FileVersionResponse::from)
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    /**
     * Pobiera konkretną wersję pliku.
     *
     * Wymaga roli VIEWER.
     *
     * Zwykły download pliku pobiera aktualną wersję,
     * a ta metoda pobiera historyczną wersję wskazaną przez versionId.
     */
    @Transactional(readOnly = true)
    public ResponseEntity<InputStreamResource> downloadVersion(UUID actorUserId,
                                                               UUID fileId,
                                                               UUID versionId) {
        /*
         * Pobieramy plik przez AccessControlService,
         * żeby jednocześnie sprawdzić istnienie pliku i uprawnienia.
         */
        FileMetadata file = accessControlService.requireFileRole(actorUserId, fileId, PermissionRole.VIEWER);

        /*
         * Wersja musi należeć do tego konkretnego fileId.
         * To chroni przed pobraniem wersji innego pliku przez podmianę versionId.
         */
        FileVersion version = versionRepository.findByIdAndFileId(versionId, fileId)
                .orElseThrow(() -> new NoSuchElementException("File version not found"));

        auditService.record(
                actorUserId,
                "FILE_VERSION_DOWNLOADED",
                ResourceType.FILE,
                fileId,
                "File version downloaded"
        );

        /*
         * Pobieramy dokładny objectKey przypisany do wersji.
         */
        InputStream inputStream = storageService.download(version.getObjectKey());

        /*
         * Nazwa pobieranego pliku zawiera numer wersji,
         * żeby użytkownik łatwo odróżnił ją od aktualnej wersji.
         */
        String filename = version.getVersionNumber() + "-" + file.getName();

        String encodedFilename = URLEncoder
                .encode(filename, StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(version.getContentType()))
                .contentLength(version.getSizeBytes())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(encodedFilename, StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(new InputStreamResource(inputStream));
    }

    /**
     * Uploaduje nową wersję istniejącego pliku.
     *
     * Wymaga roli EDITOR.
     *
     * baseVersionId jest używany do wykrywania konfliktów edycji:
     * - klient pobiera wersję A,
     * - ktoś inny zapisuje wersję B,
     * - klient próbuje zapisać zmiany oparte na A,
     * - backend widzi, że currentVersionId != baseVersionId,
     * - zamiast nadpisać cudzą pracę, tworzy conflict copy.
     */
    @Transactional
    public FileVersionUploadResponse uploadNewVersion(UUID actorUserId,
                                                      UUID fileId,
                                                      UUID baseVersionId,
                                                      MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }

        /*
         * Upload nowej wersji zmienia plik,
         * więc wymagamy roli EDITOR.
         */
        FileMetadata file = accessControlService.requireFileRole(actorUserId, fileId, PermissionRole.EDITOR);

        /*
         * Konflikt optymistyczny.
         * Jeśli klient bazował na nieaktualnej wersji, nie nadpisujemy aktualnego pliku.
         */
        if (baseVersionId != null
                && file.getCurrentVersionId() != null
                && !baseVersionId.equals(file.getCurrentVersionId())) {
            return createConflictCopy(actorUserId, file, multipartFile);
        }

        /*
         * Quota należy do właściciela pliku, niekoniecznie do aktora.
         * Przy folderach współdzielonych actor może być EDITOR-em,
         * ale przestrzeń zajmuje owner.
         */
        AppUser owner = userRepository.findByIdForUpdate(file.getOwnerId())
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        owner.reserveStorage(multipartFile.getSize());

        String contentType = multipartFile.getContentType() == null
                ? "application/octet-stream"
                : multipartFile.getContentType();

        /*
         * Nowa wersja dostaje własny objectKey.
         * Nie nadpisujemy starej wersji w storage.
         */
        String objectKey = "users/%s/files/%s/%s".formatted(
                file.getOwnerId(),
                UUID.randomUUID(),
                sanitizeFilename(multipartFile.getOriginalFilename())
        );

        /*
         * Hash nowej wersji.
         */
        String sha256 = sha256(multipartFile);

        /*
         * Fizyczny upload zawartości nowej wersji.
         */
        storageService.upload(objectKey, multipartFile);

        /*
         * Wyliczenie kolejnego numeru wersji.
         */
        int nextNumber = versionRepository.maxVersionNumber(file.getId()) + 1;

        FileVersion version = versionRepository.save(
                new FileVersion(
                        file.getId(),
                        nextNumber,
                        objectKey,
                        contentType,
                        multipartFile.getSize(),
                        sha256,
                        actorUserId,
                        false
                )
        );

        /*
         * Aktualizujemy metadane pliku tak, żeby wskazywały na nową aktualną wersję.
         */
        file.replaceContent(
                version.getId(),
                version.getVersionNumber(),
                contentType,
                multipartFile.getSize(),
                objectKey,
                sha256
        );

        fileRepository.save(file);
        userRepository.save(owner);

        auditService.record(
                actorUserId,
                "FILE_VERSION_CREATED",
                ResourceType.FILE,
                file.getId(),
                "New file version uploaded"
        );

        changeLogService.record(
                actorUserId,
                file.getOwnerId(),
                ResourceType.FILE,
                file.getId(),
                "FILE_VERSION_CREATED",
                "{\"versionId\":\"" + version.getId() + "\"}"
        );

        searchIndexService.reindexFileAsync(file.getId());

        return new FileVersionUploadResponse(
                FileResponse.from(file),
                FileVersionResponse.from(version),
                false,
                "New version created"
        );
    }

    /**
     * Przywraca starszą wersję jako aktualną wersję pliku.
     *
     * Wymaga roli EDITOR.
     *
     * Ta implementacja nie tworzy nowej fizycznej kopii pliku.
     * Przełącza currentVersionId/currentVersionNumber i metadane pliku
     * na istniejącą wersję.
     *
     * To jest oszczędne storage'owo, ale oznacza, że restore nie dodaje nowego wpisu
     * w historii wersji. W bardziej audytowalnym modelu można utworzyć nową wersję
     * typu "restore from version X".
     */
    @Transactional
    public FileVersionUploadResponse restoreVersion(UUID actorUserId, UUID fileId, UUID versionId) {
        FileMetadata file = accessControlService.requireFileRole(actorUserId, fileId, PermissionRole.EDITOR);

        /*
         * Wersja musi należeć do przywracanego pliku.
         */
        FileVersion version = versionRepository.findByIdAndFileId(versionId, fileId)
                .orElseThrow(() -> new NoSuchElementException("File version not found"));

        /*
         * Przestawienie aktualnych metadanych na starszą wersję.
         */
        file.restoreVersion(
                version.getId(),
                version.getVersionNumber(),
                version.getContentType(),
                version.getSizeBytes(),
                version.getObjectKey(),
                version.getSha256()
        );

        fileRepository.save(file);

        auditService.record(
                actorUserId,
                "FILE_VERSION_RESTORED",
                ResourceType.FILE,
                file.getId(),
                "File version restored"
        );

        changeLogService.record(
                actorUserId,
                file.getOwnerId(),
                ResourceType.FILE,
                file.getId(),
                "FILE_VERSION_RESTORED",
                "{\"versionId\":\"" + version.getId() + "\"}"
        );

        searchIndexService.reindexFileAsync(file.getId());

        return new FileVersionUploadResponse(
                FileResponse.from(file),
                FileVersionResponse.from(version),
                false,
                "Version restored"
        );
    }

    /**
     * Tworzy conflict copy, gdy klient próbuje zapisać zmianę
     * na bazie nieaktualnej wersji.
     *
     * Dzięki temu nie tracimy cudzych zmian.
     *
     * Zamiast nadpisać plik:
     * - tworzymy nowy plik w tym samym folderze,
     * - nadajemy mu nazwę typu "plik.txt (conflicted copy)",
     * - zapisujemy przesłaną zawartość jako pierwszą wersję nowego pliku.
     */
    private FileVersionUploadResponse createConflictCopy(UUID actorUserId,
                                                         FileMetadata original,
                                                         MultipartFile multipartFile) {
        /*
         * Quota nadal obciąża ownera oryginalnego pliku,
         * bo conflict copy powstaje w jego przestrzeni.
         */
        AppUser owner = userRepository.findByIdForUpdate(original.getOwnerId())
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        owner.reserveStorage(multipartFile.getSize());

        /*
         * Generujemy nazwę, która nie koliduje z istniejącymi plikami.
         */
        String conflictName = uniqueConflictName(
                original.getOwnerId(),
                original.getParentFolderId(),
                original.getName()
        );

        String contentType = multipartFile.getContentType() == null
                ? "application/octet-stream"
                : multipartFile.getContentType();

        String objectKey = "users/%s/files/%s/%s".formatted(
                original.getOwnerId(),
                UUID.randomUUID(),
                sanitizeFilename(conflictName)
        );

        String sha256 = sha256(multipartFile);

        storageService.upload(objectKey, multipartFile);

        /*
         * Conflict copy to nowy plik, nie nowa wersja oryginału.
         */
        FileMetadata conflictFile = fileRepository.save(
                new FileMetadata(
                        original.getOwnerId(),
                        original.getParentFolderId(),
                        conflictName,
                        conflictName,
                        contentType,
                        multipartFile.getSize(),
                        objectKey,
                        sha256
                )
        );

        FileVersion version = versionRepository.save(
                new FileVersion(
                        conflictFile.getId(),
                        1,
                        objectKey,
                        contentType,
                        multipartFile.getSize(),
                        sha256,
                        actorUserId,
                        true
                )
        );

        conflictFile.setInitialVersion(version.getId());
        fileRepository.save(conflictFile);
        userRepository.save(owner);

        auditService.record(
                actorUserId,
                "FILE_CONFLICT_COPY_CREATED",
                ResourceType.FILE,
                conflictFile.getId(),
                "Conflict copy created"
        );

        changeLogService.record(
                actorUserId,
                original.getOwnerId(),
                ResourceType.FILE,
                conflictFile.getId(),
                "FILE_CONFLICT_COPY_CREATED",
                "{\"sourceFileId\":\"" + original.getId() + "\"}"
        );

        searchIndexService.reindexFileAsync(conflictFile.getId());

        return new FileVersionUploadResponse(
                FileResponse.from(conflictFile),
                FileVersionResponse.from(version),
                true,
                "Base version is stale; uploaded content was saved as a conflict copy"
        );
    }

    /**
     * Generuje unikalną nazwę conflict copy.
     *
     * Jeśli "file.txt (conflicted copy)" już istnieje,
     * próbuje "file.txt (conflicted copy) 2", potem 3 itd.
     */
    private String uniqueConflictName(UUID ownerId, UUID parentFolderId, String originalName) {
        String base = originalName + " (conflicted copy)";
        String candidate = base;
        int i = 2;

        while (existsFileName(ownerId, parentFolderId, candidate)) {
            candidate = base + " " + i++;
        }

        return candidate;
    }

    /**
     * Sprawdza, czy aktywny plik o danej nazwie istnieje w folderze.
     *
     * Potrzebne do uniknięcia konfliktu nazw przy tworzeniu conflict copy.
     */
    private boolean existsFileName(UUID ownerId, UUID parentFolderId, String name) {
        return parentFolderId == null
                ? fileRepository.existsByOwnerIdAndParentFolderIdIsNullAndNameAndDeletedAtIsNull(ownerId, name)
                : fileRepository.existsByOwnerIdAndParentFolderIdAndNameAndDeletedAtIsNull(ownerId, parentFolderId, name);
    }

    /**
     * Czyści nazwę pliku przed użyciem w objectKey.
     *
     * Uwaga: nazwa wersji w storage nie musi być taka sama jak nazwa logiczna pliku.
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

    /**
     * Liczy SHA-256 przesłanej wersji.
     *
     * Hash pozwala:
     * - identyfikować zawartość wersji,
     * - wspierać deduplikację w przyszłości,
     * - diagnozować problemy integralności.
     */
    private String sha256(MultipartFile multipartFile) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            try (InputStream inputStream = multipartFile.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;

                while ((read = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }

            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IllegalStateException("Could not calculate file checksum", e);
        }
    }
}
package com.example.filestorage.file;

import com.example.filestorage.audit.AuditService;
import com.example.filestorage.auth.AppUser;
import com.example.filestorage.auth.UserRepository;
import com.example.filestorage.folder.Folder;
import com.example.filestorage.folder.FolderService;
import com.example.filestorage.sharing.AccessControlService;
import com.example.filestorage.sharing.PermissionRole;
import com.example.filestorage.sharing.ResourceType;
import com.example.filestorage.storage.StorageService;
import com.example.filestorage.sync.ChangeLogService;
import com.example.filestorage.search.SearchIndexService;
import com.example.filestorage.version.FileVersion;
import com.example.filestorage.version.FileVersionRepository;
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
 * Główny serwis biznesowy odpowiedzialny za operacje na plikach.
 *
 * To tutaj znajduje się właściwa logika aplikacji:
 * - upload i download plików,
 * - zapis metadanych w bazie,
 * - zapis binarnej zawartości w storage,
 * - kontrola quota,
 * - obsługa folderów,
 * - kontrola uprawnień,
 * - soft delete i permanent delete,
 * - wersjonowanie,
 * - audit log,
 * - changelog do synchronizacji,
 * - aktualizacja indeksu wyszukiwania.
 *
 * Controller powinien tylko delegować requesty do tej klasy.
 */
@Service
public class FileService {

    /**
     * Repozytorium metadanych plików.
     * Przechowuje informacje typu: nazwa, rozmiar, owner, parentFolderId,
     * objectKey, contentType, deletedAt, currentVersionId.
     */
    private final FileMetadataRepository fileMetadataRepository;

    /**
     * Abstrakcja nad storage, np. MinIO/S3.
     * Odpowiada za fizyczny upload, download i delete obiektów binarnych.
     */
    private final StorageService storageService;

    /**
     * Serwis folderów.
     * Używany głównie do walidacji folderu docelowego oraz sprawdzania konfliktów nazw.
     */
    private final FolderService folderService;

    /**
     * Repozytorium użytkowników.
     * Potrzebne między innymi do blokowania rekordu użytkownika przy aktualizacji quota.
     */
    private final UserRepository userRepository;

    /**
     * Serwis kontroli dostępu.
     * Sprawdza, czy użytkownik ma wymaganą rolę względem pliku lub folderu.
     */
    private final AccessControlService accessControlService;

    /**
     * Serwis audytu.
     * Rejestruje istotne operacje użytkownika, np. upload, download, rename, delete.
     */
    private final AuditService auditService;

    /**
     * Repozytorium wersji plików.
     * Każdy upload tworzy pierwszą wersję, a kolejne etapy aplikacji mogą dodawać następne.
     */
    private final FileVersionRepository fileVersionRepository;

    /**
     * Changelog używany do synchronizacji klientów.
     * Dzięki niemu klient może pobrać zmiany od ostatniego cursora zamiast listować wszystko od nowa.
     */
    private final ChangeLogService changeLogService;

    /**
     * Serwis indeksu wyszukiwania.
     * Po zmianach w pliku indeks jest aktualizowany asynchronicznie.
     */
    private final SearchIndexService searchIndexService;

    public FileService(FileMetadataRepository fileMetadataRepository,
                       StorageService storageService,
                       FolderService folderService,
                       UserRepository userRepository,
                       AccessControlService accessControlService,
                       AuditService auditService,
                       FileVersionRepository fileVersionRepository,
                       ChangeLogService changeLogService,
                       SearchIndexService searchIndexService) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.storageService = storageService;
        this.folderService = folderService;
        this.userRepository = userRepository;
        this.accessControlService = accessControlService;
        this.auditService = auditService;
        this.fileVersionRepository = fileVersionRepository;
        this.changeLogService = changeLogService;
        this.searchIndexService = searchIndexService;
    }

    /**
     * Uploaduje nowy plik.
     *
     * Najważniejsze kroki:
     * 1. Waliduje, czy plik nie jest pusty.
     * 2. Sprawdza uprawnienia do folderu docelowego, jeśli plik trafia do folderu.
     * 3. Blokuje rekord właściciela i rezerwuje miejsce w quota.
     * 4. Sprawdza konflikt nazwy w folderze.
     * 5. Liczy SHA-256 pliku.
     * 6. Uploaduje binarny plik do storage.
     * 7. Zapisuje metadane pliku w bazie.
     * 8. Tworzy pierwszą wersję pliku.
     * 9. Zapisuje audit log, changelog i odświeża indeks wyszukiwania.
     *
     * Transakcja obejmuje operacje bazodanowe.
     * Storage jest zewnętrzny, więc przy błędach produkcyjnie warto dodać cleanup/outbox.
     */
    @Transactional
    public FileResponse upload(UUID ownerId, UUID parentFolderId, MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }

        /*
         * Domyślnie właścicielem nowego pliku jest aktualny użytkownik.
         * Jeśli jednak upload odbywa się do folderu udostępnionego,
         * właścicielem zasobu pozostaje właściciel folderu.
         */
        UUID resourceOwnerId = ownerId;

        /*
         * Upload do folderu wymaga roli EDITOR.
         * VIEWER może pobierać pliki, ale nie może dodawać nowych.
         */
        if (parentFolderId != null) {
            Folder parent = accessControlService.requireFolderRole(ownerId, parentFolderId, PermissionRole.EDITOR);
            resourceOwnerId = parent.getOwnerId();
        }

        /*
         * Pobranie użytkownika z blokadą FOR UPDATE.
         * To chroni quota przed race condition, np. dwoma równoległymi uploadami,
         * które razem przekroczyłyby limit.
         */
        AppUser user = userRepository.findByIdForUpdate(resourceOwnerId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        /*
         * Rezerwacja miejsca przed uploadem.
         * Jeśli quota jest przekroczona, wyjątek powinien przerwać operację.
         */
        user.reserveStorage(multipartFile.getSize());

        /*
         * Nazwa widoczna dla użytkownika jest czyszczona z niebezpiecznych znaków.
         * Nie powinna być bezpośrednio używana jako jedyny identyfikator w storage.
         */
        String safeFilename = sanitizeFilename(multipartFile.getOriginalFilename());

        /*
         * W jednym folderze właściciel nie powinien mieć dwóch aktywnych plików
         * o tej samej nazwie.
         */
        folderService.ensureFileNameAvailable(resourceOwnerId, parentFolderId, safeFilename);

        /*
         * Jeśli klient nie poda content type, zapisujemy bezpieczny fallback.
         */
        String contentType = multipartFile.getContentType() == null
                ? "application/octet-stream"
                : multipartFile.getContentType();

        /*
         * Techniczny klucz obiektu w storage.
         * UUID zmniejsza ryzyko kolizji i pozwala zmienić nazwę pliku bez ruszania obiektu.
         */
        String objectKey = "users/%s/files/%s/%s".formatted(resourceOwnerId, UUID.randomUUID(), safeFilename);

        /*
         * Hash pliku jest potrzebny do integralności, wersjonowania i potencjalnej deduplikacji.
         */
        String sha256 = sha256(multipartFile);

        /*
         * Fizyczny upload do object storage.
         */
        storageService.upload(objectKey, multipartFile);

        /*
         * Metadane pliku trafiają do bazy.
         * Sama zawartość pliku nie jest przechowywana w PostgreSQL.
         */
        FileMetadata metadata = new FileMetadata(
                resourceOwnerId,
                parentFolderId,
                safeFilename,
                safeFilename,
                contentType,
                multipartFile.getSize(),
                objectKey,
                sha256
        );

        /*
         * Zapisujemy użytkownika z nowym storageUsedBytes.
         */
        userRepository.save(user);

        /*
         * Najpierw zapis metadanych, żeby uzyskać fileId.
         */
        FileMetadata saved = fileMetadataRepository.save(metadata);

        /*
         * Każdy nowy plik dostaje pierwszą wersję.
         * uploadedBy to ownerId, czyli użytkownik wykonujący upload,
         * nawet jeśli plik trafia do folderu należącego do innego użytkownika.
         */
        FileVersion version = fileVersionRepository.save(
                new FileVersion(
                        saved.getId(),
                        1,
                        objectKey,
                        contentType,
                        multipartFile.getSize(),
                        sha256,
                        ownerId,
                        false
                )
        );

        /*
         * Ustawienie currentVersionId/currentVersionNumber na pierwszą wersję.
         */
        saved.setInitialVersion(version.getId());
        saved = fileMetadataRepository.save(saved);

        /*
         * Audit log odpowiada na pytanie: kto wykonał operację i na jakim zasobie.
         */
        auditService.record(ownerId, "FILE_UPLOADED", ResourceType.FILE, saved.getId(), "File uploaded");

        /*
         * Changelog odpowiada za synchronizację klientów.
         */
        changeLogService.record(
                ownerId,
                saved.getOwnerId(),
                ResourceType.FILE,
                saved.getId(),
                "FILE_CREATED",
                "{\"name\":\"" + saved.getName() + "\"}"
        );

        /*
         * Indeks wyszukiwania aktualizujemy asynchronicznie,
         * żeby upload nie czekał na indexing.
         */
        searchIndexService.reindexFileAsync(saved.getId());

        return FileResponse.from(saved);
    }

    /**
     * Zwraca metadane pliku.
     *
     * Dostęp wymaga minimum roli VIEWER.
     * Dzięki temu plik może zobaczyć właściciel albo użytkownik,
     * któremu plik/folder został udostępniony.
     */
    @Transactional(readOnly = true)
    public FileResponse get(UUID actorUserId, UUID fileId) {
        return FileResponse.from(
                accessControlService.requireFileRole(actorUserId, fileId, PermissionRole.VIEWER)
        );
    }

    /**
     * Listuje aktywne pliki właściciela.
     *
     * Paginacja jest ograniczona do maksymalnie 100 elementów,
     * żeby uniknąć ciężkich zapytań i dużych odpowiedzi HTTP.
     *
     * Uwaga: ta metoda listuje pliki po ownerId.
     * Nie jest to pełny widok "shared with me".
     */
    @Transactional(readOnly = true)
    public FileListResponse list(UUID ownerId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        Page<FileMetadata> result = fileMetadataRepository.findAllByOwnerIdAndDeletedAtIsNull(
                ownerId,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        return new FileListResponse(
                result.getContent().stream().map(FileResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    /**
     * Listuje pliki w koszu użytkownika.
     *
     * Pliki w koszu nadal istnieją w bazie i storage,
     * dlatego mogą być przywrócone albo trwale usunięte.
     */
    @Transactional(readOnly = true)
    public FileListResponse trash(UUID ownerId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        Page<FileMetadata> result = fileMetadataRepository.findAllByOwnerIdAndDeletedAtIsNotNull(
                ownerId,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "deletedAt"))
        );

        return new FileListResponse(
                result.getContent().stream().map(FileResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    /**
     * Pobiera zawartość pliku jako stream HTTP.
     *
     * Dostęp wymaga minimum roli VIEWER.
     *
     * Metoda:
     * - sprawdza uprawnienia,
     * - zapisuje zdarzenie audytowe,
     * - pobiera stream z object storage,
     * - ustawia nagłówki odpowiedzi,
     * - zwraca plik jako załącznik.
     */
    @Transactional(readOnly = true)
    public ResponseEntity<InputStreamResource> download(UUID actorUserId, UUID fileId) {
        FileMetadata file = accessControlService.requireFileRole(actorUserId, fileId, PermissionRole.VIEWER);

        /*
         * Download to istotna operacja bezpieczeństwa,
         * dlatego zapisujemy ją w audycie.
         */
        auditService.record(actorUserId, "FILE_DOWNLOADED", ResourceType.FILE, file.getId(), "File downloaded");

        /*
         * Plik jest pobierany ze storage po technicznym objectKey.
         */
        InputStream inputStream = storageService.download(file.getObjectKey());

        /*
         * Kodowanie nazwy pliku zabezpiecza nagłówek Content-Disposition
         * przed problemami ze spacjami i znakami specjalnymi.
         */
        String encodedFilename = URLEncoder.encode(file.getName(), StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .contentLength(file.getSizeBytes())
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
     * Zmienia nazwę pliku.
     *
     * Wymaga roli EDITOR.
     *
     * Rename dotyczy tylko metadanych w bazie.
     * Nie trzeba zmieniać objectKey w storage, ponieważ fizyczny obiekt
     * powinien być niezależny od nazwy widocznej dla użytkownika.
     */
    @Transactional
    public FileResponse rename(UUID actorUserId, UUID fileId, RenameFileRequest request) {
        FileMetadata file = accessControlService.requireFileRole(actorUserId, fileId, PermissionRole.EDITOR);

        String newName = sanitizeFilename(request.name());

        /*
         * Jeśli nazwa realnie się zmienia, sprawdzamy konflikt w tym samym folderze.
         */
        if (!file.getName().equals(newName)) {
            folderService.ensureFileNameAvailable(file.getOwnerId(), file.getParentFolderId(), newName);
            file.rename(newName);
        }

        FileMetadata saved = fileMetadataRepository.save(file);

        auditService.record(actorUserId, "FILE_RENAMED", ResourceType.FILE, saved.getId(), "File renamed");

        changeLogService.record(
                actorUserId,
                saved.getOwnerId(),
                ResourceType.FILE,
                saved.getId(),
                "FILE_RENAMED",
                "{\"name\":\"" + saved.getName() + "\"}"
        );

        searchIndexService.reindexFileAsync(saved.getId());

        return FileResponse.from(saved);
    }

    /**
     * Przenosi plik do innego folderu.
     *
     * Wymaga roli EDITOR na pliku.
     * Jeśli folder docelowy nie jest nullem, użytkownik musi mieć też rolę EDITOR
     * na folderze docelowym.
     */
    @Transactional
    public FileResponse move(UUID actorUserId, UUID fileId, MoveFileRequest request) {
        FileMetadata file = accessControlService.requireFileRole(actorUserId, fileId, PermissionRole.EDITOR);

        UUID targetFolderId = request.parentFolderId();

        if (targetFolderId != null) {
            Folder target = accessControlService.requireFolderRole(actorUserId, targetFolderId, PermissionRole.EDITOR);

            /*
             * Na tym etapie nie wspieramy przenoszenia plików między właścicielami.
             * To upraszcza quota, ACL i strukturę katalogów.
             */
            if (!target.getOwnerId().equals(file.getOwnerId())) {
                throw new IllegalArgumentException("Moving files across owners is not supported in this stage");
            }
        }

        /*
         * Jeśli folder docelowy jest inny, sprawdzamy konflikt nazwy
         * i aktualizujemy parentFolderId.
         */
        if (!java.util.Objects.equals(file.getParentFolderId(), targetFolderId)) {
            folderService.ensureFileNameAvailable(file.getOwnerId(), targetFolderId, file.getName());
            file.moveTo(targetFolderId);
        }

        FileMetadata saved = fileMetadataRepository.save(file);

        auditService.record(actorUserId, "FILE_MOVED", ResourceType.FILE, saved.getId(), "File moved");

        changeLogService.record(
                actorUserId,
                saved.getOwnerId(),
                ResourceType.FILE,
                saved.getId(),
                "FILE_MOVED",
                "{\"parentFolderId\":\"" + saved.getParentFolderId() + "\"}"
        );

        searchIndexService.reindexFileAsync(saved.getId());

        return FileResponse.from(saved);
    }

    /**
     * Usuwa plik logicznie, czyli przenosi go do kosza.
     *
     * Wymaga roli EDITOR.
     *
     * Soft delete:
     * - ustawia deletedAt,
     * - nie usuwa fizycznego pliku ze storage,
     * - nie zwalnia quota,
     * - usuwa plik z indeksu wyszukiwania aktywnych zasobów.
     */
    @Transactional
    public void delete(UUID actorUserId, UUID fileId) {
        FileMetadata file = accessControlService.requireFileRole(actorUserId, fileId, PermissionRole.EDITOR);

        file.softDelete();
        fileMetadataRepository.save(file);

        auditService.record(actorUserId, "FILE_DELETED", ResourceType.FILE, file.getId(), "File soft-deleted");

        changeLogService.record(
                actorUserId,
                file.getOwnerId(),
                ResourceType.FILE,
                file.getId(),
                "FILE_DELETED",
                "{}"
        );

        searchIndexService.deleteAsync(file.getId());
    }

    /**
     * Przywraca plik z kosza.
     *
     * Ta metoda działa tylko dla właściciela pliku.
     * To celowo bardziej restrykcyjne niż zwykły delete/rename,
     * bo restore wpływa na strukturę katalogów właściciela.
     */
    @Transactional
    public FileResponse restore(UUID ownerId, UUID fileId, UUID parentFolderId) {
        FileMetadata file = fileMetadataRepository.findByIdAndOwnerIdAndDeletedAtIsNotNull(fileId, ownerId)
                .orElseThrow(() -> new NoSuchElementException("Deleted file not found"));

        /*
         * Folder docelowy musi istnieć i należeć do właściciela.
         * Null może oznaczać przywrócenie do root.
         */
        folderService.validateParentFolder(ownerId, parentFolderId);

        /*
         * Przed przywróceniem sprawdzamy, czy w folderze docelowym
         * nie ma aktywnego pliku o tej samej nazwie.
         */
        folderService.ensureFileNameAvailable(ownerId, parentFolderId, file.getName());

        file.restore(parentFolderId);

        FileMetadata saved = fileMetadataRepository.save(file);

        auditService.record(ownerId, "FILE_RESTORED", ResourceType.FILE, saved.getId(), "File restored");

        changeLogService.record(
                ownerId,
                saved.getOwnerId(),
                ResourceType.FILE,
                saved.getId(),
                "FILE_RESTORED",
                "{}"
        );

        searchIndexService.reindexFileAsync(saved.getId());

        return FileResponse.from(saved);
    }

    /**
     * Trwale usuwa plik z kosza.
     *
     * To operacja nieodwracalna.
     *
     * Najważniejsze kroki:
     * 1. Znajduje usunięty logicznie plik właściciela.
     * 2. Pobiera wszystkie wersje pliku.
     * 3. Usuwa każdy obiekt wersji ze storage.
     * 4. Usuwa wersje i metadane z bazy.
     * 5. Zwalnia quota użytkownika.
     * 6. Zapisuje audit, changelog i usuwa rekord z indeksu.
     */
    @Transactional
    public void permanentlyDelete(UUID ownerId, UUID fileId) {
        FileMetadata file = fileMetadataRepository.findByIdAndOwnerIdAndDeletedAtIsNotNull(fileId, ownerId)
                .orElseThrow(() -> new NoSuchElementException("Deleted file not found"));

        /*
         * Usuwamy wszystkie wersje, nie tylko aktualną.
         * Quota również powinna zostać zmniejszona o sumę rozmiarów wersji.
         */
        java.util.List<FileVersion> versions =
                fileVersionRepository
                        .findAllByFileIdOrderByVersionNumberDesc(
                                file.getId(),
                                org.springframework.data.domain.Pageable.unpaged()
                        )
                        .getContent();

        long bytesToRelease = versions.stream()
                .mapToLong(FileVersion::getSizeBytes)
                .sum();

        /*
         * Fizyczne usunięcie obiektów ze storage.
         */
        for (FileVersion version : versions) {
            storageService.delete(version.getObjectKey());
        }

        /*
         * Usuwamy rekordy wersji i metadane pliku.
         */
        fileVersionRepository.deleteAll(versions);
        fileMetadataRepository.delete(file);

        /*
         * Quota jest aktualizowana na zablokowanym rekordzie użytkownika,
         * żeby uniknąć niespójności przy równoległych operacjach.
         */
        AppUser user = userRepository.findByIdForUpdate(ownerId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        user.releaseStorage(bytesToRelease == 0 ? file.getSizeBytes() : bytesToRelease);
        userRepository.save(user);

        auditService.record(
                ownerId,
                "FILE_PERMANENTLY_DELETED",
                ResourceType.FILE,
                file.getId(),
                "File permanently deleted"
        );

        changeLogService.record(
                ownerId,
                ownerId,
                ResourceType.FILE,
                file.getId(),
                "FILE_PERMANENTLY_DELETED",
                "{}"
        );

        searchIndexService.deleteAsync(file.getId());
    }

    /**
     * Czyści nazwę pliku przed zapisem.
     *
     * Usuwa znaki, które mogą powodować problemy:
     * - nowe linie,
     * - tabulatory,
     * - slash/backslash,
     * - zbyt długie nazwy.
     *
     * To zabezpiecza zarówno UI, jak i nagłówki HTTP oraz object key.
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
     * Liczy SHA-256 pliku przesłanego przez użytkownika.
     *
     * Hash jest wykorzystywany do:
     * - weryfikacji integralności,
     * - wersjonowania,
     * - potencjalnej deduplikacji,
     * - diagnostyki i audytu.
     *
     * Plik jest czytany strumieniowo, więc metoda nie ładuje całego pliku do pamięci.
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
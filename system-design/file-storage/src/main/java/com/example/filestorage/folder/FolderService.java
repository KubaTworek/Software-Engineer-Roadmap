package com.example.filestorage.folder;

import com.example.filestorage.audit.AuditService;
import com.example.filestorage.file.FileMetadata;
import com.example.filestorage.file.FileMetadataRepository;
import com.example.filestorage.file.FileResponse;
import com.example.filestorage.sharing.AccessControlService;
import com.example.filestorage.sharing.PermissionRole;
import com.example.filestorage.sharing.ResourceType;
import com.example.filestorage.sync.ChangeLogService;
import com.example.filestorage.search.SearchIndexService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Serwis biznesowy odpowiedzialny za operacje na folderach.
 *
 * To tutaj znajduje się właściwa logika katalogów:
 * - tworzenie folderów,
 * - pobieranie folderu,
 * - listowanie dzieci folderu,
 * - zmiana nazwy,
 * - przenoszenie folderów,
 * - soft delete folderów wraz z zawartością,
 * - walidacja konfliktów nazw,
 * - ochrona przed cyklami w drzewie folderów,
 * - audit log,
 * - changelog do synchronizacji,
 * - aktualizacja indeksu wyszukiwania.
 *
 * Controller powinien tylko przekazać request do tej klasy.
 */
@Service
public class FolderService {

    /**
     * Repozytorium folderów.
     * Źródło prawdy o strukturze katalogów.
     */
    private final FolderRepository folderRepository;

    /**
     * Repozytorium metadanych plików.
     * Potrzebne do listowania plików w folderze oraz soft delete zawartości folderu.
     */
    private final FileMetadataRepository fileRepository;

    /**
     * Centralny serwis autoryzacji.
     * Sprawdza, czy użytkownik ma VIEWER/EDITOR/OWNER do folderu.
     */
    private final AccessControlService accessControlService;

    /**
     * Rejestruje istotne operacje użytkownika, np. utworzenie, rename, move, delete folderu.
     */
    private final AuditService auditService;

    /**
     * Zapisuje zdarzenia do synchronizacji klientów.
     * Klient może później pobrać zmiany przez cursor.
     */
    private final ChangeLogService changeLogService;

    /**
     * Aktualizuje pomocniczy indeks wyszukiwania folderów.
     * Indeks działa asynchronicznie i nie jest źródłem prawdy.
     */
    private final SearchIndexService searchIndexService;

    public FolderService(FolderRepository folderRepository,
                         FileMetadataRepository fileRepository,
                         AccessControlService accessControlService,
                         AuditService auditService,
                         ChangeLogService changeLogService,
                         SearchIndexService searchIndexService) {
        this.folderRepository = folderRepository;
        this.fileRepository = fileRepository;
        this.accessControlService = accessControlService;
        this.auditService = auditService;
        this.changeLogService = changeLogService;
        this.searchIndexService = searchIndexService;
    }

    /**
     * Tworzy nowy folder.
     *
     * Najważniejsze kroki:
     * 1. Ustala folder nadrzędny.
     * 2. Jeśli folder nadrzędny istnieje, wymaga roli EDITOR.
     * 3. Ustala właściciela folderu.
     * 4. Czyści nazwę folderu.
     * 5. Sprawdza konflikt nazwy w folderze docelowym.
     * 6. Zapisuje folder w bazie.
     * 7. Rejestruje audit, changelog i aktualizuje indeks wyszukiwania.
     */
    @Transactional
    public FolderResponse create(UUID actorUserId, CreateFolderRequest request) {
        UUID parentId = request.parentFolderId();

        /*
         * Domyślnie właścicielem folderu jest użytkownik, który go tworzy.
         * Jeśli jednak tworzy folder w folderze udostępnionym,
         * właścicielem pozostaje owner folderu nadrzędnego.
         */
        UUID ownerId = actorUserId;

        /*
         * Tworzenie podfolderu wymaga uprawnienia EDITOR do folderu nadrzędnego.
         * VIEWER może widzieć folder, ale nie powinien modyfikować jego zawartości.
         */
        if (parentId != null) {
            Folder parent = accessControlService.requireFolderRole(actorUserId, parentId, PermissionRole.EDITOR);
            ownerId = parent.getOwnerId();
        }

        /*
         * Nazwa folderu jest normalizowana przed zapisem.
         */
        String name = sanitizeName(request.name());

        /*
         * W jednym folderze owner nie może mieć dwóch aktywnych folderów
         * o tej samej nazwie.
         */
        ensureFolderNameAvailable(ownerId, parentId, name);

        Folder folder = new Folder(ownerId, parentId, name);
        Folder saved = folderRepository.save(folder);

        /*
         * Audit: kto utworzył folder.
         */
        auditService.record(actorUserId, "FOLDER_CREATED", ResourceType.FOLDER, saved.getId(), "Folder created");

        /*
         * Changelog: informacja dla klientów synchronizujących strukturę plików.
         */
        changeLogService.record(
                actorUserId,
                saved.getOwnerId(),
                ResourceType.FOLDER,
                saved.getId(),
                "FOLDER_CREATED",
                "{\"name\":\"" + saved.getName() + "\"}"
        );

        /*
         * Indeks wyszukiwania jest aktualizowany asynchronicznie.
         */
        searchIndexService.reindexFolderAsync(saved.getId());

        return FolderResponse.from(saved);
    }

    /**
     * Pobiera metadane folderu.
     *
     * Wymaga minimum roli VIEWER.
     * Dzięki temu właściciel i użytkownicy z dostępem do udostępnionego folderu
     * mogą zobaczyć jego metadane.
     */
    @Transactional(readOnly = true)
    public FolderResponse get(UUID actorUserId, UUID folderId) {
        return FolderResponse.from(
                accessControlService.requireFolderRole(actorUserId, folderId, PermissionRole.VIEWER)
        );
    }

    /**
     * Listuje bezpośrednie dzieci folderu.
     *
     * Jeśli folderId == null, listowany jest root aktualnego użytkownika.
     * Jeśli folderId != null, metoda wymaga VIEWER do tego folderu.
     *
     * Zwracane są osobno:
     * - foldery,
     * - pliki.
     *
     * To nie jest rekursywne listowanie całego drzewa.
     */
    @Transactional(readOnly = true)
    public FolderChildrenResponse children(UUID actorUserId, UUID folderId, int page, int size) {
        /*
         * Dla root ownerem jest aktualny użytkownik.
         */
        UUID ownerId = actorUserId;

        /*
         * Dla konkretnego folderu ownerId bierzemy z folderu.
         * To ważne przy folderach udostępnionych — actor może być inny niż owner.
         */
        if (folderId != null) {
            Folder folder = accessControlService.requireFolderRole(actorUserId, folderId, PermissionRole.VIEWER);
            ownerId = folder.getOwnerId();
        }

        return listChildrenForPublicFolder(ownerId, folderId, page, size);
    }

    /**
     * Listuje dzieci folderu bez sprawdzania uprawnień aktora.
     *
     * Metoda jest przydatna np. dla publicznych linków,
     * gdzie autoryzacja odbywa się wcześniej na podstawie tokenu linku.
     *
     * Nie powinna być używana bezpośrednio w endpointach wymagających użytkownika,
     * jeśli wcześniej nie sprawdzono dostępu.
     */
    @Transactional(readOnly = true)
    public FolderChildrenResponse listChildrenForPublicFolder(UUID ownerId, UUID folderId, int page, int size) {
        /*
         * Ograniczamy paginację.
         * Maksymalnie 100 elementów jednej kategorii na stronę.
         */
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        /*
         * Stabilne sortowanie po nazwie i ID.
         * ID jako drugi klucz zmniejsza ryzyko niestabilnej paginacji,
         * gdy wiele zasobów ma tę samą nazwę.
         */
        PageRequest pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.ASC, "name").and(Sort.by("id"))
        );

        /*
         * Pobieramy podfoldery folderu.
         * Dla root parentFolderId jest null.
         */
        Page<Folder> folders = folderId == null
                ? folderRepository.findAllByOwnerIdAndParentFolderIdIsNullAndDeletedAtIsNull(ownerId, pageable)
                : folderRepository.findAllByOwnerIdAndParentFolderIdAndDeletedAtIsNull(ownerId, folderId, pageable);

        /*
         * Pobieramy pliki leżące bezpośrednio w folderze.
         * Pliki i foldery są stronicowane osobno.
         */
        Page<FileMetadata> files = folderId == null
                ? fileRepository.findAllByOwnerIdAndParentFolderIdIsNullAndDeletedAtIsNull(ownerId, pageable)
                : fileRepository.findAllByOwnerIdAndParentFolderIdAndDeletedAtIsNull(ownerId, folderId, pageable);

        return new FolderChildrenResponse(
                folders.getContent().stream().map(FolderResponse::from).toList(),
                files.getContent().stream().map(FileResponse::from).toList(),
                safePage,
                safeSize,
                folders.getTotalElements(),
                files.getTotalElements(),
                folders.getTotalPages(),
                files.getTotalPages()
        );
    }

    /**
     * Zmienia nazwę folderu.
     *
     * Wymaga roli EDITOR.
     *
     * Rename zmienia tylko metadane folderu.
     * Nie wymaga modyfikowania plików ani obiektów w storage.
     */
    @Transactional
    public FolderResponse rename(UUID actorUserId, UUID folderId, RenameFolderRequest request) {
        Folder folder = accessControlService.requireFolderRole(actorUserId, folderId, PermissionRole.EDITOR);

        String newName = sanitizeName(request.name());

        /*
         * Jeśli nazwa się zmienia, sprawdzamy konflikt nazwy w tym samym parent folderze.
         */
        if (!folder.getName().equals(newName)) {
            ensureFolderNameAvailable(folder.getOwnerId(), folder.getParentFolderId(), newName);
            folder.rename(newName);
        }

        Folder saved = folderRepository.save(folder);

        auditService.record(actorUserId, "FOLDER_RENAMED", ResourceType.FOLDER, saved.getId(), "Folder renamed");

        changeLogService.record(
                actorUserId,
                saved.getOwnerId(),
                ResourceType.FOLDER,
                saved.getId(),
                "FOLDER_RENAMED",
                "{\"name\":\"" + saved.getName() + "\"}"
        );

        searchIndexService.reindexFolderAsync(saved.getId());

        return FolderResponse.from(saved);
    }

    /**
     * Przenosi folder do innego folderu albo do root.
     *
     * Wymaga:
     * - EDITOR do przenoszonego folderu,
     * - EDITOR do folderu docelowego, jeśli nie jest root.
     *
     * Chroni przed:
     * - przeniesieniem folderu do samego siebie,
     * - przeniesieniem folderu między właścicielami,
     * - przeniesieniem folderu do własnego potomka,
     * - konfliktem nazw w folderze docelowym.
     */
    @Transactional
    public FolderResponse move(UUID actorUserId, UUID folderId, MoveFolderRequest request) {
        Folder folder = accessControlService.requireFolderRole(actorUserId, folderId, PermissionRole.EDITOR);

        UUID newParentId = request.parentFolderId();

        /*
         * Folder nie może stać się własnym rodzicem.
         */
        if (folder.getId().equals(newParentId)) {
            throw new IllegalArgumentException("Folder cannot be moved into itself");
        }

        /*
         * Jeśli folder docelowy istnieje, użytkownik musi mieć do niego EDITOR.
         */
        if (newParentId != null) {
            Folder targetParent = accessControlService.requireFolderRole(actorUserId, newParentId, PermissionRole.EDITOR);

            /*
             * Na tym etapie nie wspieramy przenoszenia folderów między ownerami.
             * To upraszcza quota, sharing, sync i strukturę danych.
             */
            if (!targetParent.getOwnerId().equals(folder.getOwnerId())) {
                throw new IllegalArgumentException("Moving folders across owners is not supported in this stage");
            }
        }

        /*
         * Jeśli parent się nie zmienia, nie ma czego zapisywać.
         */
        if (java.util.Objects.equals(folder.getParentFolderId(), newParentId)) {
            return FolderResponse.from(folder);
        }

        /*
         * Najważniejsza walidacja drzewa:
         * nie wolno przenieść folderu do własnego potomka,
         * bo stworzyłoby to cykl.
         */
        ensureNotMovingIntoDescendant(folder.getOwnerId(), folder.getId(), newParentId);

        /*
         * W folderze docelowym nie może istnieć aktywny folder o tej samej nazwie.
         */
        ensureFolderNameAvailable(folder.getOwnerId(), newParentId, folder.getName());

        folder.moveTo(newParentId);

        Folder saved = folderRepository.save(folder);

        auditService.record(actorUserId, "FOLDER_MOVED", ResourceType.FOLDER, saved.getId(), "Folder moved");

        changeLogService.record(
                actorUserId,
                saved.getOwnerId(),
                ResourceType.FOLDER,
                saved.getId(),
                "FOLDER_MOVED",
                "{\"parentFolderId\":\"" + saved.getParentFolderId() + "\"}"
        );

        searchIndexService.reindexFolderAsync(saved.getId());

        return FolderResponse.from(saved);
    }

    /**
     * Usuwa folder logicznie razem z jego zawartością.
     *
     * Wymaga roli EDITOR.
     *
     * Soft delete:
     * - oznacza folder jako usunięty,
     * - oznacza pliki wewnątrz jako usunięte,
     * - oznacza podfoldery jako usunięte,
     * - nie usuwa fizycznych plików ze storage,
     * - nie zwalnia quota.
     */
    @Transactional
    public void delete(UUID actorUserId, UUID folderId) {
        Folder folder = accessControlService.requireFolderRole(actorUserId, folderId, PermissionRole.EDITOR);

        /*
         * Rekurencyjnie oznaczamy folder, podfoldery i pliki jako usunięte.
         */
        softDeleteRecursive(folder.getOwnerId(), folder);

        auditService.record(actorUserId, "FOLDER_DELETED", ResourceType.FOLDER, folder.getId(), "Folder soft-deleted");

        /*
         * Zapisujemy zmianę dla głównego usuniętego folderu.
         * W bardziej rozbudowanej wersji warto rozważyć changelog również dla dzieci.
         */
        changeLogService.record(
                actorUserId,
                folder.getOwnerId(),
                ResourceType.FOLDER,
                folder.getId(),
                "FOLDER_DELETED",
                "{}"
        );

        /*
         * Usuwamy folder z indeksu wyszukiwania.
         * Uwaga: dla pełnej spójności warto usuwać z indeksu również dzieci.
         */
        searchIndexService.deleteAsync(folder.getId());
    }

    /**
     * Listuje foldery znajdujące się w koszu użytkownika.
     *
     * To są foldery z ustawionym deletedAt.
     * Nadal istnieją w bazie, ale nie są widoczne w normalnym listowaniu.
     */
    @Transactional(readOnly = true)
    public FolderListResponse trash(UUID ownerId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        Page<Folder> result = folderRepository.findAllByOwnerIdAndDeletedAtIsNotNull(
                ownerId,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "deletedAt"))
        );

        return new FolderListResponse(
                result.getContent().stream().map(FolderResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    /**
     * Waliduje folder nadrzędny dla operacji wykonywanych przez ownera.
     *
     * Jeśli parentFolderId == null, oznacza to root i walidacja przechodzi.
     * Jeśli parentFolderId != null, folder musi istnieć, być aktywny i należeć do ownera.
     */
    public void validateParentFolder(UUID ownerId, UUID parentFolderId) {
        if (parentFolderId != null) {
            findActiveFolder(ownerId, parentFolderId);
        }
    }

    /**
     * Znajduje aktywny folder należący do konkretnego właściciela.
     *
     * Używane tam, gdzie operacja dotyczy przestrzeni konkretnego ownera,
     * np. restore pliku do folderu.
     */
    public Folder findActiveFolder(UUID ownerId, UUID folderId) {
        return folderRepository.findByIdAndOwnerIdAndDeletedAtIsNull(folderId, ownerId)
                .orElseThrow(() -> new NoSuchElementException("Folder not found"));
    }

    /**
     * Znajduje aktywny folder bez sprawdzania ownera.
     *
     * Przydatne w miejscach, gdzie kontrola dostępu albo owner są sprawdzane osobno.
     */
    public Folder findAnyActiveFolder(UUID folderId) {
        return folderRepository.findByIdAndDeletedAtIsNull(folderId)
                .orElseThrow(() -> new NoSuchElementException("Folder not found"));
    }

    /**
     * Sprawdza, czy w danym folderze nie istnieje aktywny plik o podanej nazwie.
     *
     * Używane przez FileService przy:
     * - uploadzie,
     * - rename pliku,
     * - move pliku,
     * - restore pliku.
     */
    public void ensureFileNameAvailable(UUID ownerId, UUID parentFolderId, String name) {
        boolean exists = parentFolderId == null
                ? fileRepository.existsByOwnerIdAndParentFolderIdIsNullAndNameAndDeletedAtIsNull(ownerId, name)
                : fileRepository.existsByOwnerIdAndParentFolderIdAndNameAndDeletedAtIsNull(ownerId, parentFolderId, name);

        if (exists) {
            throw new IllegalArgumentException("File with this name already exists in target folder");
        }
    }

    /**
     * Sprawdza, czy w danym folderze nie istnieje aktywny folder o podanej nazwie.
     *
     * Używane przy:
     * - tworzeniu folderu,
     * - rename folderu,
     * - move folderu.
     */
    private void ensureFolderNameAvailable(UUID ownerId, UUID parentFolderId, String name) {
        boolean exists = parentFolderId == null
                ? folderRepository.existsByOwnerIdAndParentFolderIdIsNullAndNameAndDeletedAtIsNull(ownerId, name)
                : folderRepository.existsByOwnerIdAndParentFolderIdAndNameAndDeletedAtIsNull(ownerId, parentFolderId, name);

        if (exists) {
            throw new IllegalArgumentException("Folder with this name already exists in target folder");
        }
    }

    /**
     * Chroni strukturę katalogów przed cyklem.
     *
     * Przykład niedozwolony:
     * /A
     *   /B
     *     /C
     *
     * Nie wolno przenieść A do C, bo wtedy A byłoby swoim własnym przodkiem.
     *
     * Metoda idzie od folderu docelowego w górę do root.
     * Jeśli po drodze znajdzie przenoszony folder, oznacza to próbę move do potomka.
     */
    private void ensureNotMovingIntoDescendant(UUID ownerId, UUID folderId, UUID possibleDescendantId) {
        UUID current = possibleDescendantId;
        Set<UUID> seen = new HashSet<>();

        while (current != null) {
            /*
             * Zabezpieczenie przed uszkodzonym drzewem już istniejącym w bazie.
             */
            if (!seen.add(current)) {
                throw new IllegalStateException("Folder tree contains a cycle");
            }

            if (folderId.equals(current)) {
                throw new IllegalArgumentException("Folder cannot be moved into its own descendant");
            }

            Folder parent = findActiveFolder(ownerId, current);
            current = parent.getParentFolderId();
        }
    }

    /**
     * Rekurencyjnie oznacza folder, jego pliki i podfoldery jako usunięte.
     *
     * To jest soft delete, nie permanent delete.
     * Nie usuwa binarnych plików ze storage i nie zwalnia quota.
     *
     * Kolejność:
     * 1. soft delete plików w folderze,
     * 2. soft delete dzieci-folderów,
     * 3. soft delete bieżącego folderu.
     */
    private void softDeleteRecursive(UUID ownerId, Folder folder) {
        /*
         * Usunięcie logiczne plików bezpośrednio w folderze.
         */
        List<FileMetadata> files =
                fileRepository.findAllByOwnerIdAndParentFolderIdAndDeletedAtIsNull(ownerId, folder.getId());

        for (FileMetadata file : files) {
            file.softDelete();
            fileRepository.save(file);
        }

        /*
         * Rekurencyjne usunięcie podfolderów.
         */
        List<Folder> children =
                folderRepository.findAllByOwnerIdAndParentFolderIdAndDeletedAtIsNull(ownerId, folder.getId());

        for (Folder child : children) {
            softDeleteRecursive(ownerId, child);
        }

        /*
         * Na końcu oznaczamy sam folder jako usunięty.
         */
        folder.softDelete();
        folderRepository.save(folder);
    }

    /**
     * Czyści i waliduje nazwę folderu.
     *
     * Reguły:
     * - nazwa nie może być pusta,
     * - usuwamy znaki nowych linii i tabulatory,
     * - slash i backslash zamieniamy na "_",
     * - maksymalna długość to 255 znaków.
     *
     * Dzięki temu nazwa folderu jest bezpieczniejsza dla UI, URL-i,
     * logów i potencjalnych ścieżek technicznych.
     */
    private String sanitizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name must not be blank");
        }

        String sanitized = name
                .replaceAll("[\\r\\n\\t]", "_")
                .replaceAll("[/\\\\]", "_")
                .trim();

        if (sanitized.isBlank()) {
            throw new IllegalArgumentException("Name must not be blank");
        }

        if (sanitized.length() > 255) {
            throw new IllegalArgumentException("Name must be at most 255 characters");
        }

        return sanitized;
    }
}
package com.example.filestorage.sharing;

import com.example.filestorage.file.FileMetadata;
import com.example.filestorage.file.FileMetadataRepository;
import com.example.filestorage.folder.Folder;
import com.example.filestorage.folder.FolderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Serwis odpowiedzialny za kontrolę dostępu do plików i folderów.
 *
 * To jedna z najważniejszych klas bezpieczeństwa w aplikacji.
 * Każda operacja typu download, rename, move, delete, upload do folderu
 * powinna przechodzić przez ten serwis albo przez serwis, który go używa.
 *
 * Model dostępu:
 * - właściciel zasobu ma implicit OWNER,
 * - użytkownik może mieć bezpośrednie uprawnienie do pliku,
 * - użytkownik może mieć bezpośrednie uprawnienie do folderu,
 * - plik/folder może dziedziczyć uprawnienia z folderów nadrzędnych.
 */
@Service
public class AccessControlService {

    /**
     * Repozytorium metadanych plików.
     * Używane do pobierania pliku i sprawdzania jego ownerId oraz parentFolderId.
     */
    private final FileMetadataRepository fileRepository;

    /**
     * Repozytorium folderów.
     * Używane do pobierania folderów i przechodzenia po drzewie rodziców.
     */
    private final FolderRepository folderRepository;

    /**
     * Repozytorium jawnie nadanych uprawnień.
     * Zawiera rekordy typu: user X ma VIEWER/EDITOR do FILE/FOLDER Y.
     */
    private final ResourcePermissionRepository permissionRepository;

    public AccessControlService(FileMetadataRepository fileRepository,
                                FolderRepository folderRepository,
                                ResourcePermissionRepository permissionRepository) {
        this.fileRepository = fileRepository;
        this.folderRepository = folderRepository;
        this.permissionRepository = permissionRepository;
    }

    /**
     * Wymusza posiadanie konkretnej roli do pliku.
     *
     * Jeśli plik nie istnieje albo jest w koszu, rzucany jest NoSuchElementException.
     * Jeśli użytkownik nie ma wymaganych uprawnień, rzucany jest SecurityException.
     *
     * Ta metoda powinna być używana wszędzie tam, gdzie operacja wymaga dostępu do pliku:
     * - VIEWER dla get/download,
     * - EDITOR dla rename/move/delete,
     * - OWNER dla operacji administracyjnych.
     */
    @Transactional(readOnly = true)
    public FileMetadata requireFileRole(UUID actorUserId, UUID fileId, PermissionRole requiredRole) {
        FileMetadata file = fileRepository.findByIdAndDeletedAtIsNull(fileId)
                .orElseThrow(() -> new NoSuchElementException("File not found"));

        if (!hasFileRole(actorUserId, file, requiredRole)) {
            throw new SecurityException("Insufficient permissions for file");
        }

        return file;
    }

    /**
     * Wymusza posiadanie konkretnej roli do folderu.
     *
     * Używane przy:
     * - uploadzie pliku do folderu,
     * - tworzeniu podfolderu,
     * - rename folderu,
     * - move folderu,
     * - listowaniu zawartości folderu,
     * - udostępnianiu folderu.
     */
    @Transactional(readOnly = true)
    public Folder requireFolderRole(UUID actorUserId, UUID folderId, PermissionRole requiredRole) {
        Folder folder = folderRepository.findByIdAndDeletedAtIsNull(folderId)
                .orElseThrow(() -> new NoSuchElementException("Folder not found"));

        if (!hasFolderRole(actorUserId, folder, requiredRole)) {
            throw new SecurityException("Insufficient permissions for folder");
        }

        return folder;
    }

    /**
     * Skrót dla operacji wymagających właściciela pliku.
     *
     * OWNER jest silniejszy niż EDITOR i VIEWER.
     * Typowe zastosowania:
     * - zarządzanie uprawnieniami,
     * - trwałe usuwanie,
     * - operacje administracyjne na zasobie.
     */
    @Transactional(readOnly = true)
    public FileMetadata requireFileOwner(UUID actorUserId, UUID fileId) {
        return requireFileRole(actorUserId, fileId, PermissionRole.OWNER);
    }

    /**
     * Skrót dla operacji wymagających właściciela folderu.
     *
     * Używane np. przy zarządzaniu sharingiem folderu.
     */
    @Transactional(readOnly = true)
    public Folder requireFolderOwner(UUID actorUserId, UUID folderId) {
        return requireFolderRole(actorUserId, folderId, PermissionRole.OWNER);
    }

    /**
     * Sprawdza, czy użytkownik ma wymaganą rolę do pliku.
     *
     * Kolejność sprawdzania jest ważna:
     * 1. Właściciel pliku zawsze ma OWNER.
     * 2. Potem sprawdzamy bezpośrednie uprawnienie do pliku.
     * 3. Na końcu sprawdzamy uprawnienia dziedziczone z folderu nadrzędnego.
     *
     * Dzięki temu plik w udostępnionym folderze jest dostępny bez osobnego
     * wpisu permission bezpośrednio na pliku.
     */
    public boolean hasFileRole(UUID actorUserId, FileMetadata file, PermissionRole requiredRole) {
        /*
         * Owner pliku ma pełny dostęp niezależnie od tabeli permissions.
         * Nie trzeba zapisywać osobnego rekordu OWNER w bazie.
         */
        if (file.getOwnerId().equals(actorUserId)) {
            return PermissionRole.OWNER.includes(requiredRole);
        }

        /*
         * Bezpośrednie uprawnienie do pliku, np. user dostał VIEWER/EDITOR
         * na konkretny plik.
         */
        if (activeDirectRole(ResourceType.FILE, file.getId(), actorUserId)
                .map(role -> role.includes(requiredRole))
                .orElse(false)) {
            return true;
        }

        /*
         * Jeśli plik znajduje się w folderze, może dziedziczyć uprawnienia
         * z folderu albo któregoś folderu nadrzędnego.
         */
        UUID parentFolderId = file.getParentFolderId();

        return parentFolderId != null
                && hasInheritedFolderRole(actorUserId, parentFolderId, requiredRole);
    }

    /**
     * Sprawdza, czy użytkownik ma wymaganą rolę do folderu.
     *
     * Kolejność jest analogiczna jak przy plikach:
     * 1. Owner folderu.
     * 2. Bezpośrednie uprawnienie do folderu.
     * 3. Uprawnienie odziedziczone z folderu nadrzędnego.
     */
    public boolean hasFolderRole(UUID actorUserId, Folder folder, PermissionRole requiredRole) {
        /*
         * Właściciel folderu ma OWNER do całego folderu.
         */
        if (folder.getOwnerId().equals(actorUserId)) {
            return PermissionRole.OWNER.includes(requiredRole);
        }

        /*
         * Bezpośrednie udostępnienie folderu użytkownikowi.
         */
        if (activeDirectRole(ResourceType.FOLDER, folder.getId(), actorUserId)
                .map(role -> role.includes(requiredRole))
                .orElse(false)) {
            return true;
        }

        /*
         * Folder może dziedziczyć dostęp z folderu nadrzędnego.
         */
        return folder.getParentFolderId() != null
                && hasInheritedFolderRole(actorUserId, folder.getParentFolderId(), requiredRole);
    }

    /**
     * Sprawdza uprawnienia dziedziczone z folderów nadrzędnych.
     *
     * Metoda idzie w górę drzewa folderów:
     * current folder -> parent -> parent parent -> ... -> root.
     *
     * Jeśli na którymkolwiek poziomie znajdzie:
     * - ownera,
     * - aktywną rolę spełniającą requiredRole,
     * wtedy zwraca true.
     *
     * To pozwala udostępnić cały folder i automatycznie dać dostęp
     * do jego podfolderów oraz plików.
     */
    private boolean hasInheritedFolderRole(UUID actorUserId, UUID folderId, PermissionRole requiredRole) {
        UUID current = folderId;

        /*
         * Zabezpieczenie przed cyklem w drzewie folderów.
         * Teoretycznie folder nie powinien być swoim przodkiem,
         * ale jeśli dane w bazie zostaną uszkodzone, nie chcemy nieskończonej pętli.
         */
        Set<UUID> seen = new HashSet<>();

        while (current != null) {
            if (!seen.add(current)) {
                throw new IllegalStateException("Folder tree contains a cycle");
            }

            Folder folder = folderRepository.findByIdAndDeletedAtIsNull(current)
                    .orElseThrow(() -> new NoSuchElementException("Folder not found"));

            /*
             * Jeśli użytkownik jest właścicielem któregoś folderu nadrzędnego,
             * ma pełny dostęp do zasobów poniżej.
             */
            if (folder.getOwnerId().equals(actorUserId)) {
                return PermissionRole.OWNER.includes(requiredRole);
            }

            /*
             * Sprawdzamy, czy na tym poziomie drzewa użytkownik ma aktywną rolę.
             * Np. jeśli dostał EDITOR do folderu /Projects,
             * to ma EDITOR również do /Projects/App/file.txt.
             */
            Optional<PermissionRole> directRole =
                    activeDirectRole(ResourceType.FOLDER, folder.getId(), actorUserId);

            if (directRole.map(role -> role.includes(requiredRole)).orElse(false)) {
                return true;
            }

            /*
             * Przejście poziom wyżej w drzewie folderów.
             */
            current = folder.getParentFolderId();
        }

        /*
         * Brak ownera i brak aktywnych permission w całym łańcuchu folderów.
         */
        return false;
    }

    /**
     * Pobiera aktywną bezpośrednią rolę użytkownika do konkretnego zasobu.
     *
     * Bezpośrednia rola oznacza wpis w tabeli permissions dokładnie dla:
     * - danego FILE albo FOLDER,
     * - konkretnego resourceId,
     * - konkretnego użytkownika.
     *
     * Metoda odrzuca uprawnienia nieaktywne, np. wygasłe albo cofnięte.
     */
    private Optional<PermissionRole> activeDirectRole(ResourceType resourceType, UUID resourceId, UUID userId) {
        return permissionRepository
                .findByResourceTypeAndResourceIdAndGranteeUserId(resourceType, resourceId, userId)
                .filter(ResourcePermission::isActive)
                .map(ResourcePermission::getRole);
    }
}
package com.example.filestorage.sharing;

import com.example.filestorage.audit.AuditService;
import com.example.filestorage.auth.AppUser;
import com.example.filestorage.auth.UserRepository;
import com.example.filestorage.file.FileMetadata;
import com.example.filestorage.file.FileMetadataRepository;
import com.example.filestorage.folder.Folder;
import com.example.filestorage.folder.FolderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Serwis biznesowy odpowiedzialny za współdzielenie plików i folderów.
 *
 * Obsługuje dwa główne mechanizmy dostępu:
 * - jawne uprawnienia dla użytkowników aplikacji,
 * - publiczne linki dostępne bez logowania.
 *
 * To krytyczny element bezpieczeństwa aplikacji, bo decyduje,
 * kto może zobaczyć albo edytować cudze zasoby.
 */
@Service
public class SharingService {

    /**
     * Centralny serwis kontroli dostępu.
     *
     * Używany do sprawdzania, czy użytkownik jest OWNER-em pliku/folderu
     * przed nadaniem albo odwołaniem dostępu.
     */
    private final AccessControlService accessControlService;

    /**
     * Repozytorium jawnych permissionów.
     *
     * Przechowuje informacje typu:
     * user X ma VIEWER/EDITOR do FILE/FOLDER Y.
     */
    private final ResourcePermissionRepository permissionRepository;

    /**
     * Repozytorium publicznych linków.
     *
     * Przechowuje hash tokenu, typ zasobu, resourceId, rolę, expiresAt i revokedAt.
     */
    private final ShareLinkRepository shareLinkRepository;

    /**
     * Repozytorium użytkowników.
     *
     * Potrzebne do znalezienia odbiorcy sharingu po adresie email.
     */
    private final UserRepository userRepository;

    /**
     * Repozytorium plików.
     *
     * Używane przy budowaniu widoku "shared with me".
     */
    private final FileMetadataRepository fileRepository;

    /**
     * Repozytorium folderów.
     *
     * Używane przy budowaniu widoku "shared with me".
     */
    private final FolderRepository folderRepository;

    /**
     * Serwis audytu.
     *
     * Rejestruje operacje bezpieczeństwa:
     * - udostępnienie zasobu,
     * - odwołanie permissionu,
     * - utworzenie publicznego linku,
     * - odwołanie publicznego linku.
     */
    private final AuditService auditService;

    /**
     * Konfiguracja sharingu.
     *
     * Zawiera np. publicBaseUrl używany do zbudowania pełnego publicznego linku.
     */
    private final SharingProperties sharingProperties;

    /**
     * Generator kryptograficznie bezpiecznych tokenów publicznych linków.
     *
     * To musi być SecureRandom, a nie zwykły Random,
     * bo token publicznego linku jest sekretem dającym dostęp do zasobu.
     */
    private final SecureRandom secureRandom = new SecureRandom();

    public SharingService(AccessControlService accessControlService,
                          ResourcePermissionRepository permissionRepository,
                          ShareLinkRepository shareLinkRepository,
                          UserRepository userRepository,
                          FileMetadataRepository fileRepository,
                          FolderRepository folderRepository,
                          AuditService auditService,
                          SharingProperties sharingProperties) {
        this.accessControlService = accessControlService;
        this.permissionRepository = permissionRepository;
        this.shareLinkRepository = shareLinkRepository;
        this.userRepository = userRepository;
        this.fileRepository = fileRepository;
        this.folderRepository = folderRepository;
        this.auditService = auditService;
        this.sharingProperties = sharingProperties;
    }

    /**
     * Udostępnia plik innemu użytkownikowi aplikacji.
     *
     * Wymaga, żeby actor był właścicielem pliku.
     * Zwykły VIEWER albo EDITOR nie powinien móc dalej rozdawać dostępu.
     */
    @Transactional
    public PermissionResponse shareFile(UUID actorUserId, UUID fileId, CreateShareRequest request) {
        /*
         * OWNER check jest najważniejszą walidacją tej operacji.
         * Bez tego każdy z dostępem do pliku mógłby eskalować sharing.
         */
        FileMetadata file = accessControlService.requireFileOwner(actorUserId, fileId);

        /*
         * Tworzy nowe uprawnienie albo aktualizuje istniejące.
         */
        ResourcePermission permission = upsertPermission(
                actorUserId,
                ResourceType.FILE,
                file.getId(),
                request
        );

        auditService.record(
                actorUserId,
                "FILE_SHARED",
                ResourceType.FILE,
                file.getId(),
                "File shared with " + request.email()
        );

        return PermissionResponse.from(permissionRepository.save(permission));
    }

    /**
     * Udostępnia folder innemu użytkownikowi aplikacji.
     *
     * Uprawnienie do folderu zwykle daje dostęp do jego zawartości
     * przez dziedziczenie w AccessControlService.
     */
    @Transactional
    public PermissionResponse shareFolder(UUID actorUserId, UUID folderId, CreateShareRequest request) {
        /*
         * Tylko właściciel folderu może zarządzać jego udostępnianiem.
         */
        Folder folder = accessControlService.requireFolderOwner(actorUserId, folderId);

        ResourcePermission permission = upsertPermission(
                actorUserId,
                ResourceType.FOLDER,
                folder.getId(),
                request
        );

        auditService.record(
                actorUserId,
                "FOLDER_SHARED",
                ResourceType.FOLDER,
                folder.getId(),
                "Folder shared with " + request.email()
        );

        return PermissionResponse.from(permissionRepository.save(permission));
    }

    /**
     * Zwraca listę bezpośrednich permissionów nadanych do pliku.
     *
     * Wymaga właściciela, bo lista osób mających dostęp do pliku
     * sama w sobie jest informacją wrażliwą.
     */
    @Transactional(readOnly = true)
    public List<PermissionResponse> listFilePermissions(UUID actorUserId, UUID fileId) {
        FileMetadata file = accessControlService.requireFileOwner(actorUserId, fileId);

        return permissionRepository
                .findAllByResourceTypeAndResourceId(ResourceType.FILE, file.getId())
                .stream()
                .map(PermissionResponse::from)
                .toList();
    }

    /**
     * Zwraca listę bezpośrednich permissionów nadanych do folderu.
     *
     * Nie rozwija uprawnień dziedziczonych.
     * Pokazuje permissiony zapisane bezpośrednio na tym folderze.
     */
    @Transactional(readOnly = true)
    public List<PermissionResponse> listFolderPermissions(UUID actorUserId, UUID folderId) {
        Folder folder = accessControlService.requireFolderOwner(actorUserId, folderId);

        return permissionRepository
                .findAllByResourceTypeAndResourceId(ResourceType.FOLDER, folder.getId())
                .stream()
                .map(PermissionResponse::from)
                .toList();
    }

    /**
     * Odwołuje jawne uprawnienie użytkownika.
     *
     * Permission nie powinien być fizycznie usuwany z bazy,
     * tylko oznaczony jako revoked, żeby zachować historię i audyt.
     */
    @Transactional
    public void revokePermission(UUID actorUserId, UUID permissionId) {
        ResourcePermission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new NoSuchElementException("Permission not found"));

        /*
         * Tylko owner zasobu może odwołać permission.
         */
        requireOwner(actorUserId, permission.getResourceType(), permission.getResourceId());

        permission.revoke();
        permissionRepository.save(permission);

        auditService.record(
                actorUserId,
                "PERMISSION_REVOKED",
                permission.getResourceType(),
                permission.getResourceId(),
                "Permission revoked"
        );
    }

    /**
     * Tworzy publiczny link do pliku.
     *
     * Link publiczny pozwala na dostęp bez logowania,
     * dlatego może go tworzyć tylko właściciel pliku.
     */
    @Transactional
    public PublicLinkResponse createFilePublicLink(UUID actorUserId,
                                                   UUID fileId,
                                                   CreatePublicLinkRequest request) {
        FileMetadata file = accessControlService.requireFileOwner(actorUserId, fileId);

        return createPublicLink(
                actorUserId,
                ResourceType.FILE,
                file.getId(),
                request
        );
    }

    /**
     * Tworzy publiczny link do folderu.
     *
     * Link do folderu pozwala anonimowo zobaczyć folder i jego zawartość,
     * zależnie od implementacji PublicLinkController.
     */
    @Transactional
    public PublicLinkResponse createFolderPublicLink(UUID actorUserId,
                                                     UUID folderId,
                                                     CreatePublicLinkRequest request) {
        Folder folder = accessControlService.requireFolderOwner(actorUserId, folderId);

        return createPublicLink(
                actorUserId,
                ResourceType.FOLDER,
                folder.getId(),
                request
        );
    }

    /**
     * Odwołuje publiczny link.
     *
     * Po tej operacji token nie powinien już dawać dostępu do zasobu.
     */
    @Transactional
    public void revokePublicLink(UUID actorUserId, UUID linkId) {
        ShareLink link = shareLinkRepository.findById(linkId)
                .orElseThrow(() -> new NoSuchElementException("Public link not found"));

        /*
         * Tylko właściciel zasobu może odwołać publiczny link.
         */
        requireOwner(actorUserId, link.getResourceType(), link.getResourceId());

        link.revoke();
        shareLinkRepository.save(link);

        auditService.record(
                actorUserId,
                "PUBLIC_LINK_REVOKED",
                link.getResourceType(),
                link.getResourceId(),
                "Public link revoked"
        );
    }

    /**
     * Waliduje token publicznego linku i zwraca aktywny link.
     *
     * To krytyczna metoda dla anonimowego dostępu.
     *
     * Sprawdza:
     * - czy token istnieje,
     * - czy link nie został odwołany,
     * - czy link nie wygasł,
     * - czy rola linku spełnia wymagany poziom dostępu.
     */
    @Transactional(readOnly = true)
    public ShareLink requireActivePublicLink(String token, PermissionRole requiredRole) {
        /*
         * W bazie nie trzymamy jawnego tokenu, tylko jego hash.
         * Dzięki temu wyciek bazy nie daje od razu działających publicznych linków.
         */
        ShareLink link = shareLinkRepository.findByTokenHash(hashToken(token))
                .orElseThrow(() -> new NoSuchElementException("Public link not found"));

        /*
         * isActive powinno uwzględniać revokedAt i expiresAt.
         * includes sprawdza hierarchię ról, np. EDITOR zawiera VIEWER.
         */
        if (!link.isActive() || !link.getRole().includes(requiredRole)) {
            throw new SecurityException("Public link is expired, revoked, or lacks permissions");
        }

        return link;
    }

    /**
     * Zwraca listę zasobów udostępnionych aktualnemu użytkownikowi.
     *
     * Bazuje na permissionach, gdzie granteeUserId == actorUserId.
     * Odfiltrowuje permissiony nieaktywne oraz zasoby usunięte.
     */
    @Transactional(readOnly = true)
    public SharedWithMeResponse sharedWithMe(UUID actorUserId) {
        List<SharedItemResponse> items = permissionRepository
                .findAllByGranteeUserIdAndRevokedAtIsNull(actorUserId)
                .stream()

                /*
                 * Dodatkowa walidacja aktywności.
                 * Powinna odfiltrować np. wygasłe permissiony.
                 */
                .filter(ResourcePermission::isActive)

                /*
                 * Zamiana permissionu na zasób widoczny dla użytkownika.
                 */
                .map(this::toSharedItem)

                /*
                 * Jeśli zasób został usunięty, toSharedItem zwróci Optional.empty().
                 */
                .flatMap(java.util.Optional::stream)
                .toList();

        return new SharedWithMeResponse(items);
    }

    /**
     * Tworzy albo aktualizuje permission dla konkretnego użytkownika.
     *
     * Upsert jest wygodny:
     * - jeśli permission już istnieje, aktualizujemy rolę i expiresAt,
     * - jeśli nie istnieje, tworzymy nowy.
     */
    private ResourcePermission upsertPermission(UUID actorUserId,
                                                ResourceType type,
                                                UUID resourceId,
                                                CreateShareRequest request) {
        /*
         * OWNER nie jest nadawany przez sharing.
         * Owner wynika z ownerId na pliku/folderze.
         */
        if (request.role() == PermissionRole.OWNER) {
            throw new IllegalArgumentException("OWNER role is implicit and cannot be granted through sharing");
        }

        /*
         * Odbiorca sharingu jest wyszukiwany po emailu.
         * Email normalizujemy do lowercase.
         */
        AppUser grantee = userRepository.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new NoSuchElementException("User with this email not found"));

        /*
         * Nie pozwalamy udostępniać zasobu samemu sobie.
         * Właściciel i tak ma implicit OWNER.
         */
        if (grantee.getId().equals(actorUserId)) {
            throw new IllegalArgumentException("You cannot share a resource with yourself");
        }

        /*
         * Jeśli permission dla tego użytkownika i zasobu już istnieje,
         * aktualizujemy go zamiast tworzyć duplikat.
         */
        return permissionRepository
                .findByResourceTypeAndResourceIdAndGranteeUserId(type, resourceId, grantee.getId())
                .map(existing -> {
                    existing.updateRole(request.role(), request.expiresAt());
                    return existing;
                })
                .orElseGet(() -> new ResourcePermission(
                        type,
                        resourceId,
                        grantee.getId(),
                        request.role(),
                        actorUserId,
                        request.expiresAt()
                ));
    }

    /**
     * Tworzy publiczny link do pliku albo folderu.
     *
     * Token jest zwracany tylko raz w PublicLinkResponse.
     * W bazie zapisywany jest wyłącznie hash tokenu.
     */
    private PublicLinkResponse createPublicLink(UUID actorUserId,
                                                ResourceType type,
                                                UUID resourceId,
                                                CreatePublicLinkRequest request) {
        /*
         * Jeśli request nie poda roli, link dostaje VIEWER.
         * To najbezpieczniejszy domyślny poziom.
         */
        PermissionRole role = request.role() == null
                ? PermissionRole.VIEWER
                : request.role();

        /*
         * Publiczny link nie może dawać OWNER.
         * Nie wolno zarządzać zasobem anonimowo.
         */
        if (role == PermissionRole.OWNER) {
            throw new IllegalArgumentException("Public links cannot grant OWNER permissions");
        }

        /*
         * Losowy token jest sekretem, który daje dostęp do zasobu.
         */
        String token = generateToken();

        /*
         * Do bazy zapisujemy hash tokenu, nie token wprost.
         */
        ShareLink link = shareLinkRepository.save(
                new ShareLink(
                        type,
                        resourceId,
                        hashToken(token),
                        role,
                        actorUserId,
                        request.expiresAt()
                )
        );

        auditService.record(
                actorUserId,
                "PUBLIC_LINK_CREATED",
                type,
                resourceId,
                "Public link created"
        );

        /*
         * Odpowiedź zawiera pełny URL z tokenem.
         * Tego tokenu nie da się później odczytać z bazy.
         */
        return PublicLinkResponse.from(
                link,
                publicUrl(token)
        );
    }

    /**
     * Sprawdza, czy actor jest ownerem zasobu.
     *
     * Używane przy odwoływaniu permissionów i publicznych linków.
     */
    private void requireOwner(UUID actorUserId, ResourceType type, UUID resourceId) {
        if (type == ResourceType.FILE) {
            accessControlService.requireFileOwner(actorUserId, resourceId);
        } else {
            accessControlService.requireFolderOwner(actorUserId, resourceId);
        }
    }

    /**
     * Zamienia permission na element widoku "shared with me".
     *
     * Jeśli zasób został usunięty logicznie, metoda zwróci Optional.empty(),
     * więc nie pokażemy martwych linków w UI.
     */
    private java.util.Optional<SharedItemResponse> toSharedItem(ResourcePermission permission) {
        if (permission.getResourceType() == ResourceType.FILE) {
            return fileRepository
                    .findByIdAndDeletedAtIsNull(permission.getResourceId())
                    .map(file -> new SharedItemResponse(
                            permission.getId(),
                            ResourceType.FILE,
                            file.getId(),
                            file.getName(),
                            permission.getRole(),
                            permission.getExpiresAt()
                    ));
        }

        return folderRepository
                .findByIdAndDeletedAtIsNull(permission.getResourceId())
                .map(folder -> new SharedItemResponse(
                        permission.getId(),
                        ResourceType.FOLDER,
                        folder.getId(),
                        folder.getName(),
                        permission.getRole(),
                        permission.getExpiresAt()
                ));
    }

    /**
     * Generuje bezpieczny token publicznego linku.
     *
     * 32 bajty losowości zakodowane URL-safe Base64 dają token trudny do zgadnięcia.
     */
    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);

        return Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    /**
     * Haszuje token publicznego linku przez SHA-256.
     *
     * W bazie przechowujemy hash tokenu.
     * Jawny token zna tylko osoba, która dostała URL.
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            return java.util.HexFormat
                    .of()
                    .formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash token", e);
        }
    }

    /**
     * Buduje publiczny URL do linku.
     *
     * Jeśli publicBaseUrl nie jest ustawiony, używany jest lokalny adres dev.
     * W produkcji publicBaseUrl powinien wskazywać publiczny adres aplikacji,
     * np. https://files.example.com.
     */
    private String publicUrl(String token) {
        String baseUrl = sharingProperties.publicBaseUrl() == null
                || sharingProperties.publicBaseUrl().isBlank()
                ? "http://localhost:8080"
                : sharingProperties.publicBaseUrl().replaceAll("/$", "");

        return baseUrl + "/api/v1/public-links/" + token;
    }
}
package com.example.filestorage.sharing;

import com.example.filestorage.config.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller odpowiedzialny za operacje sharingu.
 *
 * Ta klasa nie powinna zawierać logiki uprawnień.
 * Jej zadaniem jest:
 * - odebrać request HTTP,
 * - pobrać aktualnego użytkownika,
 * - przekazać operację do SharingService,
 * - zwrócić odpowiedź API.
 *
 * Właściwa logika, czyli:
 * - czy użytkownik może udostępnić plik/folder,
 * - czy rola jest poprawna,
 * - czy permission już istnieje,
 * - czy public link jest aktywny,
 * - czy link można odwołać,
 * powinna znajdować się w SharingService.
 */
@RestController
@RequestMapping("/api/v1")
public class SharingController {

    /**
     * Serwis biznesowy sharingu.
     *
     * Odpowiada za tworzenie permissionów, publicznych linków,
     * odwoływanie dostępu oraz budowanie widoku shared-with-me.
     */
    private final SharingService sharingService;

    public SharingController(SharingService sharingService) {
        this.sharingService = sharingService;
    }

    /**
     * Udostępnia plik innemu użytkownikowi.
     *
     * Endpoint:
     * POST /api/v1/files/{fileId}/shares
     *
     * CreateShareRequest powinien zawierać:
     * - email albo userId odbiorcy,
     * - rolę, np. VIEWER albo EDITOR,
     * - opcjonalną datę wygaśnięcia.
     *
     * SharingService powinien sprawdzić, czy currentUser ma prawo zarządzać
     * dostępem do tego pliku. W praktyce powinien to być OWNER.
     */
    @PostMapping("/files/{fileId}/shares")
    public PermissionResponse shareFile(CurrentUser currentUser,
                                        @PathVariable UUID fileId,
                                        @Valid @RequestBody CreateShareRequest request) {
        return sharingService.shareFile(
                currentUser.id(),
                fileId,
                request
        );
    }

    /**
     * Zwraca listę jawnych uprawnień nadanych do pliku.
     *
     * Endpoint:
     * GET /api/v1/files/{fileId}/shares
     *
     * To jest widok administracyjny dla ownera pliku.
     * Nie powinien być dostępny dla zwykłego VIEWER-a,
     * bo ujawniałby listę osób, które mają dostęp do zasobu.
     */
    @GetMapping("/files/{fileId}/shares")
    public List<PermissionResponse> listFileShares(CurrentUser currentUser,
                                                   @PathVariable UUID fileId) {
        return sharingService.listFilePermissions(
                currentUser.id(),
                fileId
        );
    }

    /**
     * Udostępnia folder innemu użytkownikowi.
     *
     * Endpoint:
     * POST /api/v1/folders/{folderId}/shares
     *
     * Uprawnienie do folderu jest ważniejsze niż do pojedynczego pliku,
     * bo zwykle daje dostęp również do zawartości folderu.
     *
     * SharingService powinien pilnować:
     * - czy actor jest właścicielem folderu,
     * - czy rola jest dozwolona,
     * - czy nie nadajemy dostępu samemu sobie bez potrzeby,
     * - czy permission nie dubluje istniejącego aktywnego dostępu.
     */
    @PostMapping("/folders/{folderId}/shares")
    public PermissionResponse shareFolder(CurrentUser currentUser,
                                          @PathVariable UUID folderId,
                                          @Valid @RequestBody CreateShareRequest request) {
        return sharingService.shareFolder(
                currentUser.id(),
                folderId,
                request
        );
    }

    /**
     * Zwraca listę jawnych uprawnień nadanych do folderu.
     *
     * Endpoint:
     * GET /api/v1/folders/{folderId}/shares
     *
     * Przy folderach trzeba pamiętać, że uprawnienia mogą być dziedziczone
     * przez pliki i podfoldery. Ta metoda zwykle zwraca tylko permissiony
     * nadane bezpośrednio na folderze.
     */
    @GetMapping("/folders/{folderId}/shares")
    public List<PermissionResponse> listFolderShares(CurrentUser currentUser,
                                                     @PathVariable UUID folderId) {
        return sharingService.listFolderPermissions(
                currentUser.id(),
                folderId
        );
    }

    /**
     * Odwołuje wcześniej nadane uprawnienie.
     *
     * Endpoint:
     * DELETE /api/v1/permissions/{permissionId}
     *
     * To nie powinno fizycznie usuwać rekordu permission z bazy,
     * jeśli zależy nam na audycie. Lepszy model to ustawienie revokedAt.
     *
     * SharingService powinien sprawdzić:
     * - czy permission istnieje,
     * - czy currentUser ma prawo go odwołać,
     * - czy permission nie jest już odwołany.
     */
    @DeleteMapping("/permissions/{permissionId}")
    public ResponseEntity<Void> revokePermission(CurrentUser currentUser,
                                                 @PathVariable UUID permissionId) {
        sharingService.revokePermission(
                currentUser.id(),
                permissionId
        );

        return ResponseEntity.noContent().build();
    }

    /**
     * Tworzy publiczny link do pliku.
     *
     * Endpoint:
     * POST /api/v1/files/{fileId}/public-links
     *
     * Publiczny link pozwala na dostęp bez logowania,
     * dlatego powinien być traktowany jako wrażliwy mechanizm.
     *
     * CreatePublicLinkRequest może zawierać:
     * - rolę, zwykle VIEWER,
     * - expiresAt,
     * - opcjonalnie hasło, jeśli aplikacja to wspiera.
     *
     * SharingService powinien zapisać w bazie hash tokenu, nie sam token.
     */
    @PostMapping("/files/{fileId}/public-links")
    public PublicLinkResponse createFilePublicLink(CurrentUser currentUser,
                                                   @PathVariable UUID fileId,
                                                   @RequestBody CreatePublicLinkRequest request) {
        return sharingService.createFilePublicLink(
                currentUser.id(),
                fileId,
                request
        );
    }

    /**
     * Tworzy publiczny link do folderu.
     *
     * Endpoint:
     * POST /api/v1/folders/{folderId}/public-links
     *
     * Link do folderu zwykle pozwala przeglądać jego zawartość.
     * W tym modelu download przez publiczny link działa osobno dla plików.
     *
     * Przy publicznym folderze trzeba szczególnie pilnować,
     * żeby nie pokazać zasobów usuniętych albo takich, których nie powinno być
     * w publicznym widoku.
     */
    @PostMapping("/folders/{folderId}/public-links")
    public PublicLinkResponse createFolderPublicLink(CurrentUser currentUser,
                                                     @PathVariable UUID folderId,
                                                     @RequestBody CreatePublicLinkRequest request) {
        return sharingService.createFolderPublicLink(
                currentUser.id(),
                folderId,
                request
        );
    }

    /**
     * Odwołuje publiczny link.
     *
     * Endpoint:
     * DELETE /api/v1/public-links/{linkId}
     *
     * Po tej operacji token publicznego linku nie powinien już działać.
     *
     * Tak jak przy permissionach, najbezpieczniej jest nie usuwać rekordu fizycznie,
     * tylko ustawić revokedAt, żeby zachować historię.
     */
    @DeleteMapping("/public-links/{linkId}")
    public ResponseEntity<Void> revokePublicLink(CurrentUser currentUser,
                                                 @PathVariable UUID linkId) {
        sharingService.revokePublicLink(
                currentUser.id(),
                linkId
        );

        return ResponseEntity.noContent().build();
    }

    /**
     * Zwraca zasoby udostępnione aktualnemu użytkownikowi przez innych.
     *
     * Endpoint:
     * GET /api/v1/shared-with-me
     *
     * Ten widok powinien obejmować aktywne permissiony,
     * gdzie granteeUserId == currentUser.id().
     *
     * Warto odfiltrować:
     * - odwołane permissiony,
     * - wygasłe permissiony,
     * - usunięte pliki/foldery.
     */
    @GetMapping("/shared-with-me")
    public SharedWithMeResponse sharedWithMe(CurrentUser currentUser) {
        return sharingService.sharedWithMe(currentUser.id());
    }
}
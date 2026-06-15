package com.example.filestorage.version;

import com.example.filestorage.config.CurrentUser;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * REST controller odpowiedzialny za wersje konkretnego pliku.
 *
 * Endpointy są zagnieżdżone pod:
 * /api/v1/files/{fileId}/versions
 *
 * To oznacza, że każda operacja dotyczy wersji jednego konkretnego pliku.
 *
 * Controller nie powinien zawierać logiki biznesowej.
 * Jego zadaniem jest:
 * - odebrać request HTTP,
 * - pobrać aktualnie zalogowanego użytkownika,
 * - odczytać fileId/versionId z URL,
 * - odebrać plik albo parametry requestu,
 * - przekazać wszystko do FileVersionService.
 *
 * Logika typu:
 * - czy plik istnieje,
 * - czy użytkownik ma VIEWER/EDITOR,
 * - czy wersja należy do tego pliku,
 * - czy wystąpił konflikt edycji,
 * - jak aktualizować currentVersionId,
 * powinna znajdować się w FileVersionService.
 */
@RestController
@RequestMapping("/api/v1/files/{fileId}/versions")
public class FileVersionController {

    /**
     * Serwis biznesowy wersjonowania plików.
     *
     * To on odpowiada za:
     * - tworzenie nowych wersji,
     * - pobieranie historii wersji,
     * - download konkretnej wersji,
     * - restore poprzedniej wersji,
     * - wykrywanie konfliktów przez baseVersionId.
     */
    private final FileVersionService service;

    public FileVersionController(FileVersionService service) {
        this.service = service;
    }

    /**
     * Listuje wersje konkretnego pliku.
     *
     * Endpoint:
     * GET /api/v1/files/{fileId}/versions?page=0&size=20
     *
     * currentUser:
     * użytkownik z kontekstu autoryzacji.
     *
     * fileId:
     * plik, którego historię wersji pobieramy.
     *
     * page i size:
     * podstawowa paginacja, żeby nie zwracać całej historii wersji naraz.
     *
     * FileVersionService powinien wymagać minimum roli VIEWER,
     * bo użytkownik, który może zobaczyć plik, może też zobaczyć jego wersje.
     */
    @GetMapping
    public FileVersionListResponse list(CurrentUser currentUser,
                                        @PathVariable UUID fileId,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return service.list(
                currentUser.id(),
                fileId,
                page,
                size
        );
    }

    /**
     * Uploaduje nową wersję istniejącego pliku.
     *
     * Endpoint:
     * POST /api/v1/files/{fileId}/versions
     *
     * Request jest typu multipart/form-data.
     *
     * baseVersionId:
     * opcjonalny identyfikator wersji, na której klient bazował podczas edycji.
     *
     * To jest ważne dla wykrywania konfliktów:
     * - użytkownik pobrał wersję A,
     * - ktoś inny zdążył utworzyć wersję B,
     * - użytkownik próbuje wysłać edycję bazującą na A,
     * - backend widzi, że aktualna wersja to już B,
     * - może utworzyć conflict copy albo zwrócić konflikt.
     *
     * file:
     * nowa binarna zawartość pliku.
     *
     * FileVersionService powinien wymagać roli EDITOR,
     * bo upload nowej wersji zmienia stan pliku.
     */
    @PostMapping
    public FileVersionUploadResponse uploadNewVersion(CurrentUser currentUser,
                                                      @PathVariable UUID fileId,
                                                      @RequestParam(value = "baseVersionId", required = false) UUID baseVersionId,
                                                      @RequestParam("file") MultipartFile file) {
        return service.uploadNewVersion(
                currentUser.id(),
                fileId,
                baseVersionId,
                file
        );
    }

    /**
     * Pobiera konkretną wersję pliku.
     *
     * Endpoint:
     * GET /api/v1/files/{fileId}/versions/{versionId}/download
     *
     * To różni się od zwykłego downloadu pliku:
     * - zwykły download pobiera aktualną wersję,
     * - ten endpoint pobiera dokładnie wskazaną wersję historyczną.
     *
     * FileVersionService powinien sprawdzić:
     * - czy użytkownik ma minimum VIEWER do pliku,
     * - czy versionId należy do fileId,
     * - czy wersja istnieje,
     * - jaki objectKey odpowiada tej wersji.
     */
    @GetMapping("/{versionId}/download")
    public ResponseEntity<InputStreamResource> downloadVersion(CurrentUser currentUser,
                                                               @PathVariable UUID fileId,
                                                               @PathVariable UUID versionId) {
        return service.downloadVersion(
                currentUser.id(),
                fileId,
                versionId
        );
    }

    /**
     * Przywraca wskazaną wersję jako aktualną wersję pliku.
     *
     * Endpoint:
     * POST /api/v1/files/{fileId}/versions/{versionId}/restore
     *
     * Restore nie powinien usuwać historii.
     * Najbezpieczniejszy model to utworzenie nowej wersji na podstawie starej:
     * - historia pozostaje kompletna,
     * - aktualna wersja wskazuje na odtworzoną zawartość,
     * - można później wrócić także do wersji sprzed restore.
     *
     * FileVersionService powinien wymagać roli EDITOR,
     * bo restore zmienia aktualny stan pliku.
     */
    @PostMapping("/{versionId}/restore")
    public FileVersionUploadResponse restore(CurrentUser currentUser,
                                             @PathVariable UUID fileId,
                                             @PathVariable UUID versionId) {
        return service.restoreVersion(
                currentUser.id(),
                fileId,
                versionId
        );
    }
}
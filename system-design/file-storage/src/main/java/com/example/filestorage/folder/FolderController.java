package com.example.filestorage.folder;

import com.example.filestorage.config.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller odpowiedzialny za operacje na folderach.
 *
 * Ta klasa nie zawiera logiki biznesowej.
 * Jej rola to:
 * - odebrać request HTTP,
 * - pobrać aktualnie zalogowanego użytkownika,
 * - przyjąć parametry z URL/body/query params,
 * - przekazać operację do FolderService,
 * - zwrócić odpowiedź HTTP.
 *
 * Logika typu:
 * - czy folder istnieje,
 * - czy użytkownik ma dostęp,
 * - czy nazwa jest wolna,
 * - czy move nie tworzy cyklu,
 * - czy folder jest w koszu,
 * powinna znajdować się w FolderService.
 */
@RestController
@RequestMapping("/api/v1/folders")
public class FolderController {

    /**
     * Serwis biznesowy folderów.
     *
     * Controller nie powinien sam sprawdzać uprawnień ani modyfikować encji.
     * Wszystkie reguły domenowe są delegowane do FolderService.
     */
    private final FolderService folderService;

    public FolderController(FolderService folderService) {
        this.folderService = folderService;
    }

    /**
     * Tworzy nowy folder.
     *
     * CreateFolderRequest powinien zawierać:
     * - name,
     * - opcjonalny parentFolderId.
     *
     * Jeśli parentFolderId jest null, folder powstaje w katalogu root użytkownika.
     * Jeśli parentFolderId jest ustawiony, FolderService powinien sprawdzić,
     * czy użytkownik ma prawo tworzyć zasoby w tym folderze.
     *
     * @Valid uruchamia walidację requestu, np. czy nazwa folderu nie jest pusta.
     */
    @PostMapping
    public FolderResponse create(CurrentUser currentUser,
                                 @Valid @RequestBody CreateFolderRequest request) {
        return folderService.create(currentUser.id(), request);
    }

    /**
     * Listuje zawartość katalogu root użytkownika.
     *
     * Root nie ma własnego UUID, dlatego do FolderService przekazywany jest parentFolderId = null.
     *
     * Zwracana odpowiedź powinna zawierać zarówno foldery, jak i pliki znajdujące się
     * bezpośrednio w katalogu głównym.
     *
     * page i size zapewniają podstawową paginację, żeby nie zwracać tysięcy elementów naraz.
     */
    @GetMapping("/root/children")
    public FolderChildrenResponse rootChildren(CurrentUser currentUser,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return folderService.children(currentUser.id(), null, page, size);
    }

    /**
     * Pobiera metadane konkretnego folderu.
     *
     * FolderService powinien sprawdzić:
     * - czy folder istnieje,
     * - czy nie jest usunięty,
     * - czy użytkownik ma minimum VIEWER do tego folderu.
     */
    @GetMapping("/{folderId}")
    public FolderResponse get(CurrentUser currentUser,
                              @PathVariable UUID folderId) {
        return folderService.get(currentUser.id(), folderId);
    }

    /**
     * Listuje bezpośrednią zawartość wskazanego folderu.
     *
     * Zwraca dzieci folderu, czyli:
     * - podfoldery,
     * - pliki.
     *
     * Nie powinno to rekurencyjnie zwracać całego drzewa, bo przy dużych kontach
     * byłoby to kosztowne i trudne do paginacji.
     *
     * FolderService powinien wymagać minimum VIEWER do folderu.
     */
    @GetMapping("/{folderId}/children")
    public FolderChildrenResponse children(CurrentUser currentUser,
                                           @PathVariable UUID folderId,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return folderService.children(currentUser.id(), folderId, page, size);
    }

    /**
     * Listuje foldery znajdujące się w koszu użytkownika.
     *
     * Są to foldery oznaczone jako usunięte logicznie.
     * Nie są widoczne w normalnym listowaniu, ale mogą być użyte do restore
     * albo permanent cleanup w późniejszych etapach.
     */
    @GetMapping("/trash")
    public FolderListResponse trash(CurrentUser currentUser,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return folderService.trash(currentUser.id(), page, size);
    }

    /**
     * Zmienia nazwę folderu.
     *
     * Wymaga @Valid, bo nazwa folderu powinna przejść walidację:
     * - niepusta,
     * - sensowna długość,
     * - bez niedozwolonych znaków, jeśli taką regułę ma aplikacja.
     *
     * FolderService powinien sprawdzić:
     * - czy użytkownik ma rolę EDITOR,
     * - czy w tym samym parent folderze nie istnieje folder o takiej samej nazwie.
     */
    @PatchMapping("/{folderId}/rename")
    public FolderResponse rename(CurrentUser currentUser,
                                 @PathVariable UUID folderId,
                                 @Valid @RequestBody RenameFolderRequest request) {
        return folderService.rename(currentUser.id(), folderId, request);
    }

    /**
     * Przenosi folder do innego folderu.
     *
     * MoveFolderRequest powinien zawierać docelowy parentFolderId.
     * Null może oznaczać przeniesienie do root.
     *
     * FolderService musi szczególnie uważać na:
     * - uprawnienia EDITOR do przenoszonego folderu,
     * - uprawnienia EDITOR do folderu docelowego,
     * - konflikt nazw w folderze docelowym,
     * - zakaz przeniesienia folderu do samego siebie,
     * - zakaz przeniesienia folderu do własnego potomka, bo stworzyłoby to cykl.
     */
    @PatchMapping("/{folderId}/move")
    public FolderResponse move(CurrentUser currentUser,
                               @PathVariable UUID folderId,
                               @RequestBody MoveFolderRequest request) {
        return folderService.move(currentUser.id(), folderId, request);
    }

    /**
     * Usuwa folder logicznie, czyli przenosi go do kosza.
     *
     * Ta operacja nie powinna fizycznie usuwać plików ze storage.
     * Folder i jego zawartość mogą zostać później przywrócone albo usunięte
     * przez osobny cleanup/permanent delete.
     *
     * Zwraca 204 No Content, bo klient nie potrzebuje body odpowiedzi.
     */
    @DeleteMapping("/{folderId}")
    public ResponseEntity<Void> delete(CurrentUser currentUser,
                                       @PathVariable UUID folderId) {
        folderService.delete(currentUser.id(), folderId);
        return ResponseEntity.noContent().build();
    }
}
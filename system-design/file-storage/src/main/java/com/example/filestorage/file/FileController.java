package com.example.filestorage.file;

import com.example.filestorage.config.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * REST controller odpowiedzialny za operacje na plikach użytkownika.
 *
 * Ta klasa nie zawiera logiki biznesowej.
 * Jej zadaniem jest:
 * - przyjąć żądanie HTTP,
 * - pobrać aktualnie zalogowanego użytkownika,
 * - odebrać parametry requestu,
 * - przekazać dane do FileService,
 * - zwrócić odpowiedź HTTP.
 *
 * Cała kluczowa logika, czyli walidacja właściciela, quota, zapis metadanych,
 * upload/download do storage, soft delete i restore, powinna znajdować się w FileService.
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;

    /**
     * FileService jest głównym serwisem biznesowym dla plików.
     * Controller tylko deleguje do niego operacje.
     */
    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * Upload nowego pliku.
     *
     * Obsługuje klasyczny multipart upload, czyli request z plikiem w polu "file".
     *
     * parentFolderId jest opcjonalny:
     * - null oznacza upload do głównego katalogu użytkownika,
     * - UUID oznacza upload do konkretnego folderu.
     *
     * currentUser.id() jest przekazywane do serwisu, żeby plik został przypisany
     * do właściciela i żeby FileService mógł sprawdzić quota oraz dostęp do folderu.
     */
    @PostMapping
    public FileResponse upload(CurrentUser currentUser,
                               @RequestParam(value = "parentFolderId", required = false) UUID parentFolderId,
                               @RequestParam("file") MultipartFile file) {
        return fileService.upload(currentUser.id(), parentFolderId, file);
    }

    /**
     * Listuje aktywne pliki użytkownika.
     *
     * To nie jest listowanie zawartości konkretnego folderu, tylko prosta lista plików
     * należących do użytkownika, z paginacją.
     *
     * page i size chronią API przed zwracaniem zbyt dużej liczby rekordów naraz.
     */
    @GetMapping
    public FileListResponse list(CurrentUser currentUser,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        return fileService.list(currentUser.id(), page, size);
    }

    /**
     * Zwraca pliki znajdujące się w koszu użytkownika.
     *
     * Są to pliki oznaczone jako usunięte logicznie, ale nadal istniejące w bazie
     * i object storage. Dzięki temu można je przywrócić albo trwale usunąć.
     */
    @GetMapping("/trash")
    public FileListResponse trash(CurrentUser currentUser,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        return fileService.trash(currentUser.id(), page, size);
    }

    /**
     * Pobiera metadane pojedynczego pliku.
     *
     * Zwraca informacje takie jak ID, nazwa, rozmiar, typ MIME, folder nadrzędny,
     * status usunięcia itp.
     *
     * FileService powinien sprawdzić, czy użytkownik ma prawo zobaczyć ten plik.
     */
    @GetMapping("/{fileId}")
    public FileResponse get(CurrentUser currentUser, @PathVariable UUID fileId) {
        return fileService.get(currentUser.id(), fileId);
    }

    /**
     * Pobiera fizyczną zawartość pliku.
     *
     * Controller zwraca ResponseEntity<InputStreamResource>, ponieważ plik jest streamowany
     * jako odpowiedź HTTP, zamiast zwracania JSON-a.
     *
     * FileService powinien:
     * - sprawdzić dostęp użytkownika,
     * - pobrać obiekt z object storage,
     * - ustawić nagłówki HTTP, np. Content-Type, Content-Length, Content-Disposition,
     * - zwrócić stream pliku.
     */
    @GetMapping("/{fileId}/download")
    public ResponseEntity<InputStreamResource> download(CurrentUser currentUser, @PathVariable UUID fileId) {
        return fileService.download(currentUser.id(), fileId);
    }

    /**
     * Zmienia nazwę pliku.
     *
     * @Valid uruchamia walidację RenameFileRequest, np. czy nowa nazwa nie jest pusta.
     *
     * Sama operacja powinna zmieniać tylko metadane w bazie.
     * Nie ma potrzeby zmieniać object key w storage, bo fizyczny plik może być przechowywany
     * pod technicznym identyfikatorem niezależnym od nazwy widocznej dla użytkownika.
     */
    @PatchMapping("/{fileId}/rename")
    public FileResponse rename(CurrentUser currentUser,
                               @PathVariable UUID fileId,
                               @Valid @RequestBody RenameFileRequest request) {
        return fileService.rename(currentUser.id(), fileId, request);
    }

    /**
     * Przenosi plik do innego folderu.
     *
     * MoveFileRequest powinien zawierać docelowy parentFolderId.
     * Warto dopuścić null jako przeniesienie do katalogu głównego.
     *
     * FileService powinien sprawdzić:
     * - czy plik należy do użytkownika lub czy użytkownik ma odpowiednie uprawnienia,
     * - czy folder docelowy istnieje,
     * - czy użytkownik ma dostęp do folderu docelowego,
     * - czy w folderze docelowym nie ma konfliktu nazw.
     */
    @PatchMapping("/{fileId}/move")
    public FileResponse move(CurrentUser currentUser,
                             @PathVariable UUID fileId,
                             @RequestBody MoveFileRequest request) {
        return fileService.move(currentUser.id(), fileId, request);
    }

    /**
     * Przywraca plik z kosza.
     *
     * RestoreFileRequest wskazuje folder, do którego plik ma zostać przywrócony.
     *
     * To ważne, bo oryginalny folder mógł zostać usunięty albo użytkownik może chcieć
     * przywrócić plik w inne miejsce.
     */
    @PostMapping("/{fileId}/restore")
    public FileResponse restore(CurrentUser currentUser,
                                @PathVariable UUID fileId,
                                @RequestBody RestoreFileRequest request) {
        return fileService.restore(currentUser.id(), fileId, request.parentFolderId());
    }

    /**
     * Usuwa plik logicznie, czyli przenosi go do kosza.
     *
     * Ta operacja nie powinna usuwać fizycznego obiektu ze storage.
     * Plik nadal zajmuje miejsce w quota użytkownika, bo można go przywrócić.
     *
     * Zwracamy 204 No Content, ponieważ operacja nie musi zwracać ciała odpowiedzi.
     */
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> delete(CurrentUser currentUser, @PathVariable UUID fileId) {
        fileService.delete(currentUser.id(), fileId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Trwale usuwa plik.
     *
     * To operacja destrukcyjna:
     * - usuwa metadane albo oznacza plik jako permanentnie usunięty,
     * - usuwa fizyczny obiekt z object storage,
     * - zwalnia quota użytkownika.
     *
     * Powinna być dostępna tylko dla właściciela pliku albo użytkownika z rolą OWNER.
     */
    @DeleteMapping("/{fileId}/permanent")
    public ResponseEntity<Void> permanentlyDelete(CurrentUser currentUser, @PathVariable UUID fileId) {
        fileService.permanentlyDelete(currentUser.id(), fileId);
        return ResponseEntity.noContent().build();
    }
}
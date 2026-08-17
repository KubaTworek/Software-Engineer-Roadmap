package com.example.filestorage.upload;

import com.example.filestorage.config.CurrentUser;
import com.example.filestorage.file.FileResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller odpowiedzialny za upload dużych plików.
 *
 * Ten controller obsługuje upload sesyjny:
 * - klient inicjuje upload,
 * - backend tworzy upload session,
 * - klient pobiera presigned URL dla każdego chunka,
 * - klient wysyła chunki bezpośrednio do object storage,
 * - backend potwierdza chunki,
 * - backend finalizuje upload i tworzy plik w systemie.
 *
 * To jest osobny flow od klasycznego multipart uploadu z FileController.
 * Ten wariant jest lepszy dla dużych plików, bo backend nie musi streamować
 * całej zawartości pliku przez swoje instancje.
 */
@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

    /**
     * Serwis biznesowy uploadu dużych plików.
     *
     * Controller tylko mapuje HTTP na metody UploadService.
     * Logika typu quota, walidacja chunków, status sesji, składanie pliku
     * i cleanup powinna znajdować się w UploadService.
     */
    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    /**
     * Inicjuje nową sesję uploadu.
     *
     * Endpoint:
     * POST /api/v1/uploads
     *
     * InitiateUploadRequest powinien zawierać m.in.:
     * - filename,
     * - contentType,
     * - totalSizeBytes,
     * - chunkSizeBytes,
     * - opcjonalnie parentFolderId,
     * - opcjonalnie finalSha256.
     *
     * Backend na tym etapie powinien:
     * - sprawdzić quota,
     * - sprawdzić dostęp do folderu docelowego,
     * - policzyć liczbę chunków,
     * - utworzyć upload session,
     * - zwrócić uploadId.
     *
     * Zwracamy 201 CREATED, bo tworzymy nowy zasób: upload session.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InitiateUploadResponse initiate(CurrentUser currentUser,
                                           @Valid @RequestBody InitiateUploadRequest request) {
        return uploadService.initiate(
                currentUser.id(),
                request
        );
    }

    /**
     * Pobiera stan sesji uploadu.
     *
     * Endpoint:
     * GET /api/v1/uploads/{uploadId}
     *
     * To jest kluczowe dla resumable upload.
     * Klient po zerwaniu połączenia może zapytać backend,
     * które chunki są już potwierdzone, i kontynuować od brakujących.
     */
    @GetMapping("/{uploadId}")
    public UploadSessionResponse get(CurrentUser currentUser,
                                     @PathVariable UUID uploadId) {
        return uploadService.getSession(
                currentUser.id(),
                uploadId
        );
    }

    /**
     * Generuje presigned URL do uploadu konkretnego chunka.
     *
     * Endpoint:
     * POST /api/v1/uploads/{uploadId}/chunks/{chunkIndex}/presigned-url
     *
     * Backend nie przyjmuje tutaj danych pliku.
     * Zwraca tylko tymczasowy URL typu PUT, pod który klient wyśle chunk
     * bezpośrednio do object storage.
     *
     * UploadService powinien sprawdzić:
     * - czy upload session należy do użytkownika,
     * - czy sesja jest aktywna,
     * - czy chunkIndex mieści się w zakresie,
     * - jaki objectKey ma dostać dany chunk.
     */
    @PostMapping("/{uploadId}/chunks/{chunkIndex}/presigned-url")
    public PresignedChunkUrlResponse signedUrl(CurrentUser currentUser,
                                               @PathVariable UUID uploadId,
                                               @PathVariable int chunkIndex) {
        return uploadService.presignedUrl(
                currentUser.id(),
                uploadId,
                chunkIndex
        );
    }

    /**
     * Potwierdza, że konkretny chunk został wysłany do object storage.
     *
     * Endpoint:
     * POST /api/v1/uploads/{uploadId}/chunks/{chunkIndex}/complete
     *
     * Klient woła ten endpoint dopiero po udanym PUT do presigned URL.
     *
     * CompleteChunkRequest powinien zawierać m.in.:
     * - sizeBytes,
     * - sha256 chunka.
     *
     * UploadService powinien:
     * - sprawdzić, czy obiekt chunka istnieje w storage,
     * - porównać rozmiar,
     * - zapisać hash chunka,
     * - oznaczyć chunk jako ukończony,
     * - obsłużyć retry idempotentnie.
     */
    @PostMapping("/{uploadId}/chunks/{chunkIndex}/complete")
    public ChunkResponse completeChunk(CurrentUser currentUser,
                                       @PathVariable UUID uploadId,
                                       @PathVariable int chunkIndex,
                                       @Valid @RequestBody CompleteChunkRequest request) {
        return uploadService.completeChunk(
                currentUser.id(),
                uploadId,
                chunkIndex,
                request
        );
    }

    /**
     * Finalizuje upload.
     *
     * Endpoint:
     * POST /api/v1/uploads/{uploadId}/complete
     *
     * Ta operacja powinna być możliwa dopiero wtedy,
     * gdy wszystkie chunki są poprawnie przesłane i potwierdzone.
     *
     * UploadService powinien:
     * - sprawdzić kompletność chunków,
     * - złożyć chunki w finalny obiekt,
     * - opcjonalnie zweryfikować finalSha256,
     * - utworzyć metadane pliku,
     * - utworzyć pierwszą wersję pliku,
     * - oznaczyć upload session jako COMPLETED,
     * - zapisać audit/changelog/search index,
     * - opcjonalnie usunąć tymczasowe chunki.
     */
    @PostMapping("/{uploadId}/complete")
    public FileResponse completeUpload(CurrentUser currentUser,
                                       @PathVariable UUID uploadId) {
        return uploadService.completeUpload(
                currentUser.id(),
                uploadId
        );
    }

    /**
     * Anuluje upload.
     *
     * Endpoint:
     * DELETE /api/v1/uploads/{uploadId}
     *
     * Używane, gdy klient rezygnuje z uploadu albo chce wyczyścić niedokończoną sesję.
     *
     * UploadService powinien:
     * - sprawdzić właściciela sesji,
     * - oznaczyć sesję jako ABORTED,
     * - usunąć tymczasowe chunki ze storage,
     * - zwolnić ewentualnie zarezerwowane zasoby, jeśli quota była rezerwowana wcześniej.
     *
     * Zwracamy 204 NO CONTENT, bo po anulowaniu nie trzeba zwracać body.
     */
    @DeleteMapping("/{uploadId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void abort(CurrentUser currentUser,
                      @PathVariable UUID uploadId) {
        uploadService.abort(
                currentUser.id(),
                uploadId
        );
    }
}
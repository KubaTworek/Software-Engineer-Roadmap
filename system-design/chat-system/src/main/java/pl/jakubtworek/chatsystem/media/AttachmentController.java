package pl.jakubtworek.chatsystem.media;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.chatsystem.auth.UserPrincipal;

import java.util.UUID;

/**
 * REST controller odpowiedzialny za obsługę załączników.
 *
 * W aplikacji czatu załącznik nie powinien być wysyłany bezpośrednio
 * razem z wiadomością tekstową.
 *
 * Poprawny flow wygląda tak:
 * 1. Klient tworzy upload przez POST /api/attachments/upload-url.
 * 2. Backend zwraca attachmentId oraz uploadToken/uploadUrl.
 * 3. Klient wysyła binarną zawartość pliku osobnym requestem.
 * 4. Załącznik przechodzi w status gotowy do użycia.
 * 5. Klient wysyła wiadomość z attachmentIds.
 *
 * Dzięki temu MessageService nie musi przyjmować dużych payloadów binarnych,
 * a sama wiadomość przechowuje tylko referencje do załączników.
 */
@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    /**
     * Serwis zawierający właściwą logikę załączników.
     *
     * Controller nie zapisuje plików samodzielnie.
     * Deleguje do AttachmentService:
     * - walidację metadanych,
     * - generowanie tokenu uploadu,
     * - zapis zawartości,
     * - kontrolę właściciela,
     * - pobieranie pliku.
     */
    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    /**
     * Tworzy nowy upload załącznika.
     *
     * Endpoint:
     * POST /api/attachments/upload-url
     *
     * Request zawiera metadane pliku, np.:
     * - fileName,
     * - contentType,
     * - sizeBytes.
     *
     * Response powinien zawierać:
     * - attachmentId,
     * - uploadToken albo uploadUrl,
     * - status załącznika,
     * - informacje potrzebne klientowi do uploadu.
     *
     * W produkcyjnej architekturze ten endpoint zwykle zwraca pre-signed URL
     * do S3/MinIO/GCS.
     *
     * W tej lokalnej implementacji upload idzie przez backend,
     * ale model API nadal przypomina pre-signed upload flow.
     */
    @PostMapping("/upload-url")
    public AttachmentResponse createUpload(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateAttachmentRequest request
    ) {
        /*
         * principal.id() staje się właścicielem załącznika.
         *
         * To później pozwala sprawdzić,
         * czy użytkownik może użyć attachmentId podczas wysyłania wiadomości.
         */
        return attachmentService.createUpload(principal.id(), request);
    }

    /**
     * Uploaduje binarną zawartość załącznika.
     *
     * Endpoint:
     * PUT /api/attachments/{attachmentId}/content?uploadToken=...
     *
     * Ten endpoint przyjmuje surowe bajty pliku.
     *
     * uploadToken pełni rolę prostego, jednorazowego/tymczasowego uprawnienia
     * do zapisania zawartości konkretnego attachmentId.
     *
     * Dzięki temu sam attachmentId nie wystarcza do podmiany pliku.
     */
    @PutMapping(value = "/{attachmentId}/content", consumes = MediaType.ALL_VALUE)
    public AttachmentResponse uploadContent(
            @PathVariable UUID attachmentId,
            @RequestParam String uploadToken,
            @RequestBody byte[] content
    ) {
        /*
         * AttachmentService powinien sprawdzić:
         * - czy attachment istnieje,
         * - czy uploadToken jest poprawny,
         * - czy rozmiar contentu zgadza się z deklaracją,
         * - czy content type jest dozwolony,
         * - gdzie zapisać plik,
         * - czy oznaczyć attachment jako READY.
         */
        return attachmentService.uploadContent(
                attachmentId,
                uploadToken,
                content
        );
    }

    /**
     * Pobiera zawartość załącznika.
     *
     * Endpoint:
     * GET /api/attachments/{attachmentId}/content
     *
     * principal.id() służy do kontroli dostępu.
     *
     * Użytkownik nie powinien móc pobrać dowolnego pliku tylko dlatego,
     * że zna attachmentId.
     *
     * AttachmentService powinien sprawdzić:
     * - czy attachment istnieje,
     * - czy użytkownik jest właścicielem albo członkiem rozmowy,
     *   w której załącznik został użyty,
     * - czy plik jest gotowy do pobrania.
     */
    @GetMapping("/{attachmentId}/content")
    public ResponseEntity<byte[]> downloadContent(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID attachmentId
    ) {
        /*
         * W tej wersji zwracamy same bajty z HTTP 200 OK.
         *
         * Produkcyjnie warto dodać też nagłówki:
         * - Content-Type,
         * - Content-Disposition,
         * - Content-Length,
         * albo zamiast tego zwracać signed download URL do object storage.
         */
        return ResponseEntity.ok(
                attachmentService.downloadContent(principal.id(), attachmentId)
        );
    }
}
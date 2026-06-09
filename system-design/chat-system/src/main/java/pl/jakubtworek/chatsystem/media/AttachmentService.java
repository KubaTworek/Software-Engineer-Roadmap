package pl.jakubtworek.chatsystem.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.chatsystem.common.BadRequestException;
import pl.jakubtworek.chatsystem.common.ForbiddenException;
import pl.jakubtworek.chatsystem.common.NotFoundException;
import pl.jakubtworek.chatsystem.user.AppUser;
import pl.jakubtworek.chatsystem.user.UserRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Serwis odpowiedzialny za obsługę załączników.
 *
 * W tej implementacji pliki są zapisywane lokalnie na dysku,
 * ale API jest zaprojektowane podobnie do pre-signed URL flow.
 *
 * Typowy flow:
 * 1. Klient wywołuje createUpload() z metadanymi pliku.
 * 2. Backend tworzy Attachment w bazie i generuje uploadToken.
 * 3. Klient wysyła bajty pliku przez uploadContent().
 * 4. Attachment przechodzi w status UPLOADED.
 * 5. Klient wysyła wiadomość z attachmentIds.
 * 6. MessageService sprawdza, czy attachmenty należą do nadawcy i są gotowe.
 *
 * Ważne:
 * MessageService nie powinien przyjmować dużych binarnych payloadów.
 * Wiadomość powinna przechowywać tylko referencje do gotowych attachmentów.
 */
@Service
public class AttachmentService {

    /**
     * Maksymalny rozmiar pojedynczego pliku: 25 MB.
     *
     * Limit chroni backend i storage przed zbyt dużymi uploadami.
     */
    private static final long MAX_SIZE_BYTES = 25L * 1024 * 1024;

    /**
     * Dozwolone typy plików.
     *
     * image/ pozwala na image/png, image/jpeg itd.
     * text/ pozwala na text/plain itd.
     * application/pdf pozwala na PDF.
     *
     * To jest podstawowy allowlist.
     * Produkcyjnie warto dodatkowo weryfikować realny typ pliku po zawartości,
     * a nie ufać wyłącznie mimeType z requestu.
     */
    private static final List<String> ALLOWED_MIME_PREFIXES =
            List.of("image/", "text/", "application/pdf");

    /**
     * Repozytorium metadanych załączników.
     *
     * Plik jest na dysku, ale metadane są w bazie:
     * owner, fileName, mimeType, size, storageKey, uploadToken, status.
     */
    private final AttachmentRepository attachmentRepository;

    /**
     * Repozytorium użytkowników.
     *
     * Potrzebne do przypisania właściciela attachmentu.
     */
    private final UserRepository userRepository;

    /**
     * Lokalny katalog storage.
     *
     * Domyślnie ./data/uploads.
     *
     * W produkcji ten element powinien być zastąpiony object storage,
     * np. S3, MinIO, GCS albo Azure Blob.
     */
    private final Path storageDir;

    /**
     * Generator bezpiecznych tokenów uploadu.
     *
     * Token pozwala przesłać zawartość pliku bez ponownej autoryzacji właściciela
     * w samym endpointcie uploadContent().
     */
    private final SecureRandom secureRandom = new SecureRandom();

    public AttachmentService(
            AttachmentRepository attachmentRepository,
            UserRepository userRepository,
            @Value("${app.media.local-dir:./data/uploads}") String storageDir
    ) {
        this.attachmentRepository = attachmentRepository;
        this.userRepository = userRepository;
        this.storageDir = Path.of(storageDir);
    }

    /**
     * Tworzy metadane uploadu i zwraca klientowi uploadUrl/uploadToken.
     *
     * Ta metoda nie zapisuje jeszcze bajtów pliku.
     * Tworzy tylko rekord Attachment w statusie oczekującym na upload.
     */
    @Transactional
    public AttachmentResponse createUpload(UUID currentUserId, CreateAttachmentRequest request) {

        /*
         * Walidujemy metadane przed utworzeniem attachmentu:
         * - rozmiar,
         * - mime type,
         * - nazwę pliku.
         */
        validateMetadata(
                request.fileName(),
                request.mimeType(),
                request.sizeBytes()
        );

        /*
         * Attachment musi mieć właściciela.
         * Właścicielem jest aktualnie zalogowany użytkownik.
         */
        AppUser owner = userRepository.findById(currentUserId)
                .orElseThrow(() -> new NotFoundException("Current user not found"));

        /*
         * Token uploadu działa jak lokalny odpowiednik pre-signed URL.
         *
         * Klient musi go podać przy uploadContent().
         */
        String token = randomToken();

        /*
         * storageKey wskazuje, gdzie plik zostanie zapisany.
         *
         * Dodajemy userId i losowy UUID, żeby:
         * - rozdzielić pliki użytkowników,
         * - uniknąć kolizji nazw,
         * - nie ufać bezpośrednio nazwie pliku z requestu.
         */
        String storageKey =
                currentUserId + "/" + UUID.randomUUID() + "-" + sanitizeFileName(request.fileName());

        /*
         * Zapisujemy metadane attachmentu.
         * Sam plik binarny zostanie zapisany później w uploadContent().
         */
        Attachment attachment = attachmentRepository.save(
                new Attachment(
                        owner,
                        request.fileName().trim(),
                        request.mimeType().trim(),
                        request.sizeBytes(),
                        storageKey,
                        token
                )
        );

        return toResponse(attachment);
    }

    /**
     * Zapisuje binarną zawartość pliku dla wcześniej utworzonego attachmentu.
     *
     * Endpoint używa attachmentId + uploadToken.
     * To zabezpiecza przed prostą podmianą pliku po samym ID.
     */
    @Transactional
    public AttachmentResponse uploadContent(
            UUID attachmentId,
            String uploadToken,
            byte[] content
    ) {
        /*
         * Szukamy attachmentu po ID i tokenie.
         *
         * Jeśli token jest błędny, traktujemy to jako Forbidden.
         */
        Attachment attachment = attachmentRepository
                .findByIdAndUploadToken(attachmentId, uploadToken)
                .orElseThrow(() -> new ForbiddenException("Invalid upload token"));

        /*
         * Nie przyjmujemy pustych plików.
         */
        if (content.length == 0) {
            throw new BadRequestException("File content cannot be empty");
        }

        /*
         * Twardy limit po realnym rozmiarze przesłanego contentu.
         *
         * Nie wystarczy ufać sizeBytes z createUpload().
         */
        if (content.length > MAX_SIZE_BYTES) {
            throw new BadRequestException("File is too large");
        }

        try {
            /*
             * Wyliczamy docelową ścieżkę pliku.
             *
             * normalize() porządkuje ścieżkę, ale samo w sobie nie wystarcza
             * jako pełna ochrona przed path traversal.
             * Tu storageKey jest generowany przez backend, więc ryzyko jest ograniczone.
             */
            Path target = storageDir
                    .resolve(attachment.getStorageKey())
                    .normalize();

            /*
             * Tworzymy katalog użytkownika, jeśli jeszcze nie istnieje.
             */
            Files.createDirectories(target.getParent());

            /*
             * Zapisujemy plik na lokalnym dysku.
             */
            Files.write(target, content);

            /*
             * Aktualizujemy status attachmentu i realny rozmiar po uploadzie.
             */
            attachment.markUploaded(content.length);
            attachmentRepository.save(attachment);

            return toResponse(attachment);
        } catch (IOException ex) {
            /*
             * Błąd storage mapujemy na BadRequestException w tej wersji.
             * Produkcyjnie lepiej użyć osobnego wyjątku technicznego, np. StorageException.
             */
            throw new BadRequestException("Could not store file: " + ex.getMessage());
        }
    }

    /**
     * Pobiera zawartość pliku.
     *
     * Obecna wersja pozwala pobrać plik tylko właścicielowi attachmentu.
     *
     * To jest celowe uproszczenie Etapu 5.
     * Docelowo użytkownik powinien móc pobrać attachment,
     * jeśli jest członkiem konwersacji, w której ten attachment został wysłany.
     */
    @Transactional(readOnly = true)
    public byte[] downloadContent(UUID currentUserId, UUID attachmentId) {

        /*
         * Attachment musi istnieć w bazie.
         */
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new NotFoundException("Attachment not found"));

        /*
         * Nie pobieramy pliku, który nie został jeszcze poprawnie uploadowany.
         */
        if (attachment.getStatus() != AttachmentStatus.UPLOADED) {
            throw new BadRequestException("Attachment is not available for download");
        }

        /*
         * Uproszczona kontrola dostępu:
         * tylko właściciel może pobrać attachment bezpośrednio.
         *
         * Produkcyjnie trzeba tu dodać sprawdzenie,
         * czy attachment został użyty w wiadomości dostępnej dla currentUserId.
         */
        if (!attachment.getOwner().getId().equals(currentUserId)) {
            throw new ForbiddenException("You cannot download this attachment directly");
        }

        try {
            /*
             * Odczytujemy bajty z lokalnego storage.
             */
            return Files.readAllBytes(
                    storageDir.resolve(attachment.getStorageKey()).normalize()
            );
        } catch (IOException ex) {
            /*
             * Metadane attachmentu istnieją, ale pliku nie ma na dysku.
             * To oznacza niespójność między bazą a storage.
             */
            throw new NotFoundException("Attachment file not found in local object storage");
        }
    }

    /**
     * Pobiera i waliduje attachmenty, które mają zostać dołączone do wiadomości.
     *
     * Ta metoda jest używana przez MessageService przy wysyłaniu wiadomości.
     *
     * Jej zadanie:
     * - upewnić się, że attachmenty istnieją,
     * - należą do nadawcy,
     * - są już uploadowane,
     * - nie przekraczają limitu liczby attachmentów na wiadomość.
     */
    @Transactional(readOnly = true)
    public List<Attachment> getReadyAttachmentsOwnedBy(
            UUID ownerId,
            List<UUID> attachmentIds
    ) {
        /*
         * Brak attachmentów jest poprawnym przypadkiem:
         * wiadomość może być czysto tekstowa.
         */
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return List.of();
        }

        /*
         * Limit liczby attachmentów na wiadomość.
         *
         * Chroni UI, backend i storage przed nadużyciami.
         */
        if (attachmentIds.size() > 10) {
            throw new BadRequestException("A message can contain at most 10 attachments");
        }

        /*
         * Pobieramy attachmenty z bazy.
         */
        List<Attachment> attachments = attachmentRepository.findAllById(attachmentIds);

        /*
         * Jeśli liczba pobranych attachmentów różni się od liczby ID,
         * to przynajmniej jeden attachment nie istnieje.
         */
        if (attachments.size() != attachmentIds.size()) {
            throw new NotFoundException("One or more attachments were not found");
        }

        for (Attachment attachment : attachments) {
            /*
             * Użytkownik może wysłać tylko własne attachmenty.
             *
             * Bez tego można byłoby podpiąć cudzy attachmentId do swojej wiadomości.
             */
            if (!attachment.getOwner().getId().equals(ownerId)) {
                throw new ForbiddenException("You can only send your own attachments");
            }

            /*
             * Attachment musi być uploadowany przed wysłaniem wiadomości.
             *
             * Nie pozwalamy wysłać wiadomości z attachmentem,
             * który istnieje tylko jako metadata bez pliku.
             */
            if (attachment.getStatus() != AttachmentStatus.UPLOADED) {
                throw new BadRequestException("Attachment must be uploaded before it can be sent");
            }
        }

        return attachments;
    }

    /**
     * Waliduje metadane pliku przed utworzeniem uploadu.
     *
     * To jest pierwsza linia obrony przed nadużyciami storage.
     */
    private void validateMetadata(String fileName, String mimeType, long sizeBytes) {

        /*
         * Sprawdzamy deklarowany rozmiar pliku.
         */
        if (sizeBytes > MAX_SIZE_BYTES) {
            throw new BadRequestException("File is too large. Max size is 25 MB");
        }

        /*
         * Sprawdzamy typ MIME po allowliście.
         *
         * Uwaga: mimeType pochodzi od klienta, więc nie można mu w pełni ufać.
         * To jest szybka walidacja, nie pełne skanowanie pliku.
         */
        boolean allowed = ALLOWED_MIME_PREFIXES.stream()
                .anyMatch(prefix -> mimeType.startsWith(prefix) || mimeType.equals(prefix));

        if (!allowed) {
            throw new BadRequestException("Unsupported file type");
        }

        /*
         * Blokujemy oczywiste próby path traversal w nazwie pliku.
         *
         * storageKey i tak jest generowany przez backend,
         * ale lepiej nie przechowywać podejrzanej nazwy nawet jako metadata.
         */
        if (fileName.contains("..")
                || fileName.contains("/")
                || fileName.contains("\\")) {
            throw new BadRequestException("Invalid file name");
        }
    }

    /**
     * Buduje odpowiedź AttachmentResponse.
     *
     * uploadUrl zawiera endpoint i uploadToken potrzebne do przesłania pliku.
     *
     * downloadUrl jest zwracany dopiero wtedy,
     * gdy plik ma status UPLOADED.
     */
    private AttachmentResponse toResponse(Attachment attachment) {
        String uploadUrl =
                "/api/attachments/" + attachment.getId()
                        + "/content?uploadToken=" + attachment.getUploadToken();

        String downloadUrl = attachment.getStatus() == AttachmentStatus.UPLOADED
                ? "/api/attachments/" + attachment.getId() + "/content"
                : null;

        return AttachmentResponse.from(
                attachment,
                uploadUrl,
                downloadUrl
        );
    }

    /**
     * Generuje bezpieczny losowy token uploadu.
     *
     * Token ma 32 bajty losowości i jest kodowany jako URL-safe Base64.
     *
     * withoutPadding() usuwa znaki "=",
     * dzięki czemu token wygodnie nadaje się do użycia w URL.
     */
    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    /**
     * Czyści nazwę pliku z nietypowych znaków.
     *
     * Do storageKey dopuszczamy tylko:
     * - litery,
     * - cyfry,
     * - kropkę,
     * - underscore,
     * - myślnik.
     *
     * Pozostałe znaki zamieniamy na "_".
     */
    private String sanitizeFileName(String fileName) {
        return fileName.trim()
                .replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
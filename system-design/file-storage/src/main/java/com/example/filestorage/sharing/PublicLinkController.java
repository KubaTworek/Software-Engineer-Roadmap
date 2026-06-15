package com.example.filestorage.sharing;

import com.example.filestorage.audit.AuditService;
import com.example.filestorage.file.FileMetadata;
import com.example.filestorage.file.FileMetadataRepository;
import com.example.filestorage.file.FileResponse;
import com.example.filestorage.folder.Folder;
import com.example.filestorage.folder.FolderChildrenResponse;
import com.example.filestorage.folder.FolderResponse;
import com.example.filestorage.folder.FolderService;
import com.example.filestorage.storage.StorageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;

/**
 * REST controller obsługujący publiczne linki do plików i folderów.
 *
 * To API działa bez zalogowanego użytkownika.
 * Dostęp jest oparty o token publicznego linku.
 *
 * Najważniejsze zasady bezpieczeństwa:
 * - token musi być zweryfikowany przez SharingService,
 * - link musi być aktywny i niewygasły,
 * - link musi mieć odpowiednią rolę, tutaj minimum VIEWER,
 * - zasób nadal musi istnieć i nie może być w koszu,
 * - każde użycie publicznego linku powinno trafić do audit logu.
 */
@RestController
@RequestMapping("/api/v1/public-links")
public class PublicLinkController {

    /**
     * Serwis sharingu.
     *
     * Odpowiada za walidację tokenu publicznego linku.
     * Controller nie powinien sam porównywać tokenów ani sprawdzać hashy.
     */
    private final SharingService sharingService;

    /**
     * Repozytorium plików.
     *
     * Używane do pobrania metadanych pliku wskazanego przez publiczny link.
     */
    private final FileMetadataRepository fileRepository;

    /**
     * Serwis folderów.
     *
     * Używany do pobrania folderu i listowania jego publicznej zawartości.
     */
    private final FolderService folderService;

    /**
     * Serwis object storage.
     *
     * Używany do pobrania fizycznej zawartości pliku przy publicznym downloadzie.
     */
    private final StorageService storageService;

    /**
     * Serwis audytu.
     *
     * Publiczne linki są wrażliwym mechanizmem,
     * dlatego warto logować ich otwarcia i pobrania.
     */
    private final AuditService auditService;

    public PublicLinkController(SharingService sharingService,
                                FileMetadataRepository fileRepository,
                                FolderService folderService,
                                StorageService storageService,
                                AuditService auditService) {
        this.sharingService = sharingService;
        this.fileRepository = fileRepository;
        this.folderService = folderService;
        this.storageService = storageService;
        this.auditService = auditService;
    }

    /**
     * Rozwiązuje publiczny link.
     *
     * Endpoint:
     * GET /api/v1/public-links/{token}
     *
     * Dla linku do pliku:
     * - zwraca metadane pliku.
     *
     * Dla linku do folderu:
     * - zwraca metadane folderu,
     * - zwraca pierwszą stronę jego zawartości.
     *
     * Zwracany typ to Object, bo metoda obsługuje dwa różne warianty odpowiedzi:
     * FileResponse albo PublicFolderResponse.
     *
     * W większym API rozważyłbym jawny wrapper z polem resourceType,
     * żeby kontrakt odpowiedzi był bardziej przewidywalny.
     */
    @GetMapping("/{token}")
    public Object resolve(@PathVariable String token,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "20") int size) {
        /*
         * Najważniejszy krok bezpieczeństwa:
         * token musi odpowiadać aktywnemu publicznemu linkowi
         * z uprawnieniem minimum VIEWER.
         *
         * SharingService powinien sprawdzić m.in.:
         * - hash tokenu,
         * - revokedAt,
         * - expiresAt,
         * - permission role.
         */
        ShareLink link = sharingService.requireActivePublicLink(token, PermissionRole.VIEWER);

        /*
         * actorUserId = null, bo dostęp odbywa się anonimowo przez publiczny link.
         */
        auditService.record(
                null,
                "PUBLIC_LINK_VIEWED",
                link.getResourceType(),
                link.getResourceId(),
                "Public link opened"
        );

        /*
         * Publiczny link może wskazywać na plik.
         * Wtedy zwracamy tylko metadane, bez streamowania zawartości.
         * Pobranie pliku jest osobnym endpointem /download.
         */
        if (link.getResourceType() == ResourceType.FILE) {
            FileMetadata file = fileRepository.findByIdAndDeletedAtIsNull(link.getResourceId())
                    .orElseThrow(() -> new NoSuchElementException("File not found"));

            return FileResponse.from(file);
        }

        /*
         * Jeśli link wskazuje na folder, pobieramy folder i jego dzieci.
         * Zawartość folderu jest stronicowana.
         */
        Folder folder = folderService.findAnyActiveFolder(link.getResourceId());

        /*
         * Ta metoda nie sprawdza CurrentUser, bo użytkownik jest anonimowy.
         * Autoryzacja została wykonana wcześniej przez token publicznego linku.
         */
        FolderChildrenResponse children = folderService.listChildrenForPublicFolder(
                folder.getOwnerId(),
                folder.getId(),
                page,
                size
        );

        return new PublicFolderResponse(
                FolderResponse.from(folder),
                children
        );
    }

    /**
     * Pobiera plik przez publiczny link.
     *
     * Endpoint:
     * GET /api/v1/public-links/{token}/download
     *
     * Ten endpoint działa tylko dla linków wskazujących na FILE.
     * Jeśli token wskazuje na folder, zwracany jest błąd.
     */
    @GetMapping("/{token}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable String token) {
        /*
         * Link musi być aktywny i mieć minimum VIEWER.
         * Bez tego anonimowy użytkownik nie powinien dostać żadnych danych.
         */
        ShareLink link = sharingService.requireActivePublicLink(token, PermissionRole.VIEWER);

        /*
         * Download ma sens tylko dla pliku.
         * Folder można przeglądać przez resolve(), ale nie pobierać jako plik.
         */
        if (link.getResourceType() != ResourceType.FILE) {
            throw new IllegalArgumentException("This public link points to a folder, not a file");
        }

        /*
         * Pobieramy aktywny plik.
         * Jeśli plik został usunięty po utworzeniu linku, link nie powinien działać.
         */
        FileMetadata file = fileRepository.findByIdAndDeletedAtIsNull(link.getResourceId())
                .orElseThrow(() -> new NoSuchElementException("File not found"));

        /*
         * Audit downloadu przez publiczny link.
         * actorUserId = null, bo użytkownik nie jest zalogowany.
         */
        auditService.record(
                null,
                "PUBLIC_LINK_DOWNLOADED",
                ResourceType.FILE,
                file.getId(),
                "File downloaded through public link"
        );

        /*
         * Pobranie fizycznej zawartości pliku ze storage.
         */
        InputStream inputStream = storageService.download(file.getObjectKey());

        /*
         * Nazwa pliku trafia do nagłówka Content-Disposition.
         * Kodowanie zabezpiecza spacje i znaki specjalne.
         */
        String encodedFilename = URLEncoder
                .encode(file.getName(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        /*
         * Zwracamy plik jako załącznik HTTP.
         */
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .contentLength(file.getSizeBytes())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(encodedFilename, StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(new InputStreamResource(inputStream));
    }
}
package com.example.filestorage.production.cdn;

import com.example.filestorage.audit.AuditService;
import com.example.filestorage.config.CurrentUser;
import com.example.filestorage.sharing.AccessControlService;
import com.example.filestorage.sharing.PermissionRole;
import com.example.filestorage.sharing.ResourceType;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Produkcyjny controller pobierania plików.
 *
 * Zamiast zwracać binarną zawartość pliku przez backend,
 * endpoint zwraca krótko ważny podpisany URL do pobrania pliku.
 *
 * Typowy flow:
 * 1. Klient woła GET /api/v1/files/{fileId}/download-url.
 * 2. Backend sprawdza, czy użytkownik ma dostęp do pliku.
 * 3. Backend tworzy signed URL do CDN/object storage.
 * 4. Klient pobiera plik bezpośrednio z CDN/storage.
 *
 * To odciąża aplikację, bo duże pliki nie przechodzą przez instancje backendu.
 */
@RestController
@RequestMapping("/api/v1/files")
public class ProductionDownloadController {

    /**
     * Centralny serwis kontroli dostępu.
     *
     * Musi zostać wywołany przed wygenerowaniem signed URL,
     * bo sam URL daje bezpośredni dostęp do obiektu w storage/CDN.
     */
    private final AccessControlService accessControlService;

    /**
     * Serwis generujący podpisane URL-e.
     *
     * Może generować:
     * - presigned GET URL do S3/MinIO,
     * - podpisany URL CDN,
     * - URL z tokenem czasowym.
     */
    private final CdnService cdnService;

    /**
     * Serwis audytu.
     *
     * Rejestruje fakt utworzenia linku do pobrania.
     * To nie jest dokładnie to samo co faktyczny download,
     * ale w tym modelu backend widzi przynajmniej moment wydania URL-a.
     */
    private final AuditService auditService;

    public ProductionDownloadController(AccessControlService accessControlService,
                                        CdnService cdnService,
                                        AuditService auditService) {
        this.accessControlService = accessControlService;
        this.cdnService = cdnService;
        this.auditService = auditService;
    }

    /**
     * Tworzy podpisany URL do pobrania pliku.
     *
     * Endpoint:
     * GET /api/v1/files/{fileId}/download-url
     *
     * Wymaga minimum roli VIEWER.
     *
     * Zwraca DownloadUrlResponse, czyli zwykle:
     * - url,
     * - method,
     * - expiresAt.
     */
    @GetMapping("/{fileId}/download-url")
    public DownloadUrlResponse downloadUrl(CurrentUser currentUser,
                                           @PathVariable UUID fileId) {
        /*
         * Najważniejszy krok bezpieczeństwa.
         *
         * Nie wolno generować signed URL przed sprawdzeniem ACL.
         * Signed URL omija później backend, więc dostęp musi być rozstrzygnięty tutaj.
         */
        var file = accessControlService.requireFileRole(
                currentUser.id(),
                fileId,
                PermissionRole.VIEWER
        );

        /*
         * Zapis audytu: użytkownik dostał signed URL do pliku.
         *
         * Uwaga: to nie gwarantuje, że użytkownik faktycznie pobrał plik,
         * bo sam download odbędzie się poza backendem.
         */
        auditService.record(
                currentUser.id(),
                "FILE_DOWNLOAD_URL_CREATED",
                ResourceType.FILE,
                file.getId(),
                "Signed download URL created"
        );

        /*
         * Generujemy podpisany URL na podstawie objectKey pliku.
         *
         * CdnService powinien ustawić krótki TTL,
         * żeby URL nie był ważny zbyt długo po wydaniu.
         */
        return cdnService.signedDownloadUrl(file.getObjectKey());
    }
}
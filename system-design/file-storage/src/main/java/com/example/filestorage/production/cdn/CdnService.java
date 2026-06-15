package com.example.filestorage.production.cdn;

import com.example.filestorage.storage.StorageService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Serwis odpowiedzialny za generowanie linków do pobierania plików.
 *
 * W produkcyjnym modelu backend nie powinien streamować dużych plików przez siebie.
 * Zamiast tego po sprawdzeniu uprawnień generuje URL, z którego klient pobiera plik
 * bezpośrednio z CDN albo object storage.
 *
 * Ta klasa nie sprawdza uprawnień.
 * Zakłada, że AccessControlService został wywołany wcześniej,
 * np. w ProductionDownloadController.
 */
@Service
public class CdnService {

    /**
     * Konfiguracja CDN.
     *
     * Zawiera m.in.:
     * - enabled,
     * - baseUrl,
     * - signedUrlTtlSeconds.
     */
    private final CdnProperties properties;

    /**
     * Serwis object storage.
     *
     * Używany jako fallback, gdy CDN nie jest włączony albo nie ma baseUrl.
     * Wtedy generowany jest presigned GET URL bezpośrednio do storage.
     */
    private final StorageService storageService;

    public CdnService(CdnProperties properties,
                      StorageService storageService) {
        this.properties = properties;
        this.storageService = storageService;
    }

    /**
     * Generuje URL do pobrania obiektu.
     *
     * objectKey:
     * techniczny klucz pliku w object storage, np.
     * users/{userId}/files/{uuid}/filename.pdf
     *
     * Działanie:
     * - ustala TTL linku,
     * - jeśli CDN jest włączony i ma baseUrl, buduje URL CDN,
     * - w przeciwnym razie tworzy presigned GET URL do object storage.
     *
     * Zwracany DownloadUrlResponse informuje klienta:
     * - jaki URL ma użyć,
     * - czy źródłem jest CDN czy OBJECT_STORAGE,
     * - kiedy URL wygasa.
     */
    public DownloadUrlResponse signedDownloadUrl(String objectKey) {
        /*
         * Minimalny TTL to 60 sekund.
         *
         * To chroni przed błędną konfiguracją typu 0 albo kilka sekund,
         * która mogłaby powodować wygasanie linku zanim klient zdąży pobrać plik.
         */
        long ttl = Math.max(properties.signedUrlTtlSeconds(), 60);

        Instant expiresAt = Instant.now().plusSeconds(ttl);

        /*
         * Ścieżka CDN.
         *
         * Jeśli CDN jest włączony i ma publiczny baseUrl,
         * zwracamy URL w formacie:
         * {baseUrl}/{objectKey}
         *
         * Uwaga: w tej implementacji URL CDN nie jest realnie podpisywany.
         * To jest raczej prosty publiczny CDN URL z kontrolowanym expiresAt w odpowiedzi.
         * Jeżeli CDN wymaga podpisanych URL-i, trzeba dodać podpis kryptograficzny
         * albo token zależny od dostawcy CDN.
         */
        if (properties.enabled()
                && properties.baseUrl() != null
                && !properties.baseUrl().isBlank()) {
            String url = properties.baseUrl().replaceAll("/$", "")
                    + "/"
                    + objectKey;

            return new DownloadUrlResponse(
                    url,
                    "CDN",
                    expiresAt
            );
        }

        /*
         * Fallback do object storage.
         *
         * Tu URL jest faktycznie presigned, czyli storage wymusi TTL
         * i pozwoli pobrać obiekt tylko przez ograniczony czas.
         */
        return new DownloadUrlResponse(
                storageService.presignedGetUrl(
                        objectKey,
                        Duration.ofSeconds(ttl)
                ),
                "OBJECT_STORAGE",
                expiresAt
        );
    }
}
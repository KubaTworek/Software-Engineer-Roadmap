package com.example.filestorage.production.cdn;

import com.example.filestorage.storage.StorageService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.web.util.UriUtils;

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
    private final Clock clock;

    public CdnService(CdnProperties properties,
                      StorageService storageService,
                      Clock clock) {
        this.properties = properties;
        this.storageService = storageService;
        this.clock = clock;
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

        Instant expiresAt = clock.instant().plusSeconds(ttl);

        /*
         * Ścieżka CDN.
         *
         * Jeśli CDN jest włączony i ma publiczny baseUrl,
         * zwracamy URL w formacie:
         * {baseUrl}/{objectKey}
         *
         * Podpis HMAC obejmuje sciezke i czas wygasniecia. CDN/edge musi
         * zweryfikowac oba pola tym samym sekretem; samo pole expiresAt w JSON
         * nie stanowi zabezpieczenia zasobu.
         */
        if (properties.enabled()
                && properties.baseUrl() != null
                && !properties.baseUrl().isBlank()) {
            if (properties.signingSecret() == null || properties.signingSecret().length() < 32) {
                throw new IllegalStateException("CDN signing secret must contain at least 32 characters");
            }
            long expires = expiresAt.getEpochSecond();
            String path = "/" + UriUtils.encodePath(objectKey, StandardCharsets.UTF_8);
            String signature = hmacSha256(path + "\n" + expires, properties.signingSecret());
            String url = properties.baseUrl().replaceAll("/$", "")
                    + path + "?expires=" + expires + "&signature=" + signature;

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

    private String hmacSha256(String value, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not sign CDN URL", exception);
        }
    }
}

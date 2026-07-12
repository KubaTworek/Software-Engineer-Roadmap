package com.example.videostreaming.drm;

import com.example.videostreaming.auth.User;
import com.example.videostreaming.catalog.Video;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * Serwis do generowania podpisanych cookies i tokenów playbackowych.
 *
 * Główna odpowiedzialność:
 * - tworzy signed cookies dla CDN,
 * - tworzy playback token dla DRM license endpointu,
 * - weryfikuje playback token,
 * - podpisuje dane przy pomocy HMAC-SHA256.
 *
 * Używany przez:
 * - PlaybackController do wydania czasowego dostępu do odtwarzania,
 * - DrmLicenseController do sprawdzenia, czy request o licencję DRM
 *   pochodzi z poprawnego flow playbacku.
 *
 * Ważne:
 * Ten serwis nie sprawdza subskrypcji ani geo-blockingu.
 * Zakłada, że decyzja dostępu została już podjęta wcześniej
 * przez EntitlementService i GeoService.
 */
@Service
public class SignedCookieService {

    /**
     * Sekret używany do podpisywania polityk i tokenów.
     *
     * Domyślnie może używać app.jwt.secret,
     * ale produkcyjnie lepiej mieć osobny sekret dla playback/CDN.
     */
    private final String secret;

    /**
     * Opcjonalna domena cookie.
     *
     * Przydatna, gdy API i CDN działają pod wspólną domeną,
     * np. api.example.com i cdn.example.com z cookie domain .example.com.
     */
    private final String cookieDomain;

    /**
     * Czy cookie ma mieć flagę Secure.
     *
     * Produkcyjnie powinno być true,
     * żeby cookie było wysyłane tylko po HTTPS.
     */
    private final boolean secure;

    public SignedCookieService(
            @Value("${app.premium.signed-cookie-secret:${app.jwt.secret}}") String secret,
            @Value("${app.premium.cookie-domain:}") String cookieDomain,
            @Value("${app.premium.cookie-secure:false}") boolean secure) {
        this.secret = secret;
        this.cookieDomain = cookieDomain;
        this.secure = secure;
    }

    /**
     * Tworzy signed cookies dla playbacku danego filmu.
     *
     * Cookies:
     * - VS-Policy: zawiera zakodowaną politykę dostępu,
     * - VS-Signature: zawiera podpis HMAC tej polityki.
     *
     * Policy zawiera:
     * - userId,
     * - videoId,
     * - timestamp wygaśnięcia.
     *
     * CDN albo warstwa edge może później sprawdzić:
     * - czy policy nie wygasła,
     * - czy signature pasuje do policy,
     * - czy użytkownik ma dostęp do ścieżki danego filmu.
     *
     * @param user użytkownik, dla którego wydajemy dostęp
     * @param video film, do którego wydajemy dostęp
     * @param ttl czas ważności cookies
     * @return lista cookies do ustawienia w odpowiedzi HTTP
     */
    public List<ResponseCookie> createPlaybackCookies(User user, Video video, Duration ttl) {
        Instant expires = Instant.now().plus(ttl);

        /*
         * Prosta polityka dostępu.
         *
         * W MVP jest tekstowa i czytelna.
         * Produkcyjnie często stosuje się JSON policy zgodny z konkretnym CDN,
         * np. CloudFront signed cookies albo własny edge authorization format.
         */
        String policy = "user=" + user.getId()
                + ";video=" + video.getId()
                + ";exp=" + expires.getEpochSecond();

        String signature = sign(policy);

        return List.of(
                cookie("VS-Policy", base64(policy), ttl),
                cookie("VS-Signature", signature, ttl)
        );
    }

    /**
     * Tworzy krótko ważny playback token dla DRM.
     *
     * Token jest powiązany z:
     * - konkretnym użytkownikiem,
     * - konkretnym filmem,
     * - czasem wygaśnięcia.
     *
     * Format:
     * base64(userId:videoId:exp).signature
     *
     * Ten token jest później wysyłany przez player do /api/drm/license.
     * DrmLicenseController sprawdza go przed wydaniem licencji.
     */
    public String createPlaybackToken(User user, Video video, Duration ttl) {
        Instant expires = Instant.now().plus(ttl);

        String claims = user.getId()
                + ":"
                + video.getId()
                + ":"
                + expires.getEpochSecond();

        return base64(claims) + "." + sign(claims);
    }

    /**
     * Weryfikuje playback token używany przy wydawaniu licencji DRM.
     *
     * Sprawdza:
     * - czy token istnieje i ma separator ".",
     * - czy claims da się zdekodować,
     * - czy userId zgadza się z aktualnym użytkownikiem,
     * - czy videoId zgadza się z filmem,
     * - czy token nie wygasł,
     * - czy podpis HMAC jest poprawny.
     *
     * Dzięki temu token wydany dla jednego filmu/użytkownika
     * nie może zostać użyty do pobrania licencji dla innej treści.
     */
    public boolean verifyPlaybackToken(String token, String expectedUserId, String expectedVideoId) {
        if (token == null || !token.contains(".")) {
            return false;
        }

        String[] parts = token.split("\\.", 2);

        String claims = new String(
                Base64.getUrlDecoder().decode(parts[0]),
                StandardCharsets.UTF_8
        );

        String[] values = claims.split(":");

        if (values.length != 3) {
            return false;
        }

        if (!values[0].equals(expectedUserId) || !values[1].equals(expectedVideoId)) {
            return false;
        }

        long exp = Long.parseLong(values[2]);

        if (Instant.now().getEpochSecond() > exp) {
            return false;
        }

        return sign(claims).equals(parts[1]);
    }

    /**
     * Buduje pojedyncze cookie HTTP.
     *
     * Ustawienia:
     * - httpOnly: JavaScript w przeglądarce nie odczyta cookie,
     * - secure: zależne od konfiguracji, produkcyjnie true,
     * - sameSite=Lax: ogranicza wysyłanie cookie w kontekstach cross-site,
     * - path=/ : cookie dostępne dla całej domeny,
     * - maxAge=ttl: cookie wygasa razem z polityką playbacku.
     *
     * Jeśli cookieDomain jest ustawione, cookie zostanie przypisane
     * do tej domeny, np. .example.com.
     */
    private ResponseCookie cookie(String name, String value, Duration ttl) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(ttl);

        if (cookieDomain != null && !cookieDomain.isBlank()) {
            builder.domain(cookieDomain);
        }

        return builder.build();
    }

    /**
     * Podpisuje wartość przy pomocy HMAC-SHA256.
     *
     * HMAC zapewnia integralność:
     * jeśli ktoś zmieni policy albo claims,
     * podpis przestanie się zgadzać.
     *
     * Wynik jest kodowany jako Base64 URL-safe bez paddingu,
     * żeby nadawał się do cookies i tokenów.
     */
    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");

            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));

            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(
                            mac.doFinal(value.getBytes(StandardCharsets.UTF_8))
                    );
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign playback policy", e);
        }
    }

    /**
     * Koduje tekst jako Base64 URL-safe bez paddingu.
     *
     * Używane do:
     * - zakodowania policy w cookie,
     * - zakodowania claims w playback tokenie.
     */
    private String base64(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
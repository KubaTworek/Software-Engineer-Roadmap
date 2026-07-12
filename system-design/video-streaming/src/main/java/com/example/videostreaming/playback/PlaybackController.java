package com.example.videostreaming.playback;

import com.example.videostreaming.auth.User;
import com.example.videostreaming.catalog.Video;
import com.example.videostreaming.catalog.VideoRepository;
import com.example.videostreaming.catalog.VideoStatus;
import com.example.videostreaming.drm.SignedCookieService;
import com.example.videostreaming.geo.GeoService;
import com.example.videostreaming.premium.EntitlementService;
import com.example.videostreaming.storage.ObjectStorageService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.example.videostreaming.playback.PlaybackDtos.*;

/**
 * Kontroler odpowiedzialny za rozpoczęcie odtwarzania VOD.
 *
 * Główna odpowiedzialność:
 * - sprawdza, czy film jest gotowy do odtworzenia,
 * - sprawdza geo-blocking,
 * - sprawdza entitlement użytkownika,
 * - wystawia signed cookies dla CDN,
 * - generuje tymczasowy signed URL do manifestu HLS,
 * - generuje playback token dla DRM, jeśli film jest chroniony,
 * - zwraca playerowi komplet informacji potrzebnych do startu playbacku.
 *
 * Ważne:
 * Ten kontroler NIE streamuje segmentów wideo.
 * Segmenty powinny być pobierane przez player bezpośrednio z CDN/object storage.
 */
@RestController
@RequestMapping("/api/playback")
public class PlaybackController {

    /**
     * Repozytorium filmów.
     *
     * Używane do pobrania filmu, jego statusu,
     * HLS master object key oraz polityk premium/DRM.
     */
    private final VideoRepository videos;

    /**
     * Serwis storage/CDN.
     *
     * Używany do:
     * - wygenerowania signed GET URL do manifestu,
     * - zbudowania CDN URL do manifestu HLS.
     */
    private final ObjectStorageService storage;

    /**
     * Serwis entitlementów.
     *
     * Decyduje, czy użytkownik ma prawo odtworzyć film.
     * Może uwzględniać plan subskrypcji, politykę premium i geo-blocking.
     */
    private final EntitlementService entitlements;

    /**
     * Serwis geo.
     *
     * Rozpoznaje kraj użytkownika na podstawie requestu
     * i sprawdza, czy film jest dostępny w tym kraju.
     */
    private final GeoService geo;

    /**
     * Serwis signed cookies/tokenów.
     *
     * Używany do:
     * - wygenerowania cookies autoryzujących dostęp do CDN,
     * - wygenerowania playback tokenu dla DRM license endpointu.
     */
    private final SignedCookieService signedCookies;

    /**
     * Metryka poprawnie rozpoczętych playbacków.
     *
     * Zwiększana po przejściu wszystkich checków dostępu.
     */
    private final Counter playbackStarted;

    /**
     * Metryka odrzuconych prób playbacku.
     *
     * Zwiększana, gdy użytkownik nie ma uprawnień do filmu.
     */
    private final Counter playbackDenied;

    public PlaybackController(VideoRepository videos,
                              ObjectStorageService storage,
                              EntitlementService entitlements,
                              GeoService geo,
                              SignedCookieService signedCookies,
                              MeterRegistry registry) {
        this.videos = videos;
        this.storage = storage;
        this.entitlements = entitlements;
        this.geo = geo;
        this.signedCookies = signedCookies;

        this.playbackStarted = Counter.builder("video_playback_started_total")
                .register(registry);

        this.playbackDenied = Counter.builder("video_playback_denied_total")
                .register(registry);
    }

    /**
     * Przygotowuje odtwarzanie filmu dla playera.
     *
     * Typowy flow:
     * 1. Klient wywołuje endpoint po kliknięciu "Play".
     * 2. Backend pobiera film.
     * 3. Backend sprawdza, czy film jest PUBLISHED i ma manifest HLS.
     * 4. Backend rozpoznaje kraj użytkownika.
     * 5. Backend sprawdza geo-blocking.
     * 6. Backend sprawdza entitlement użytkownika.
     * 7. Backend ustawia signed cookies dla CDN.
     * 8. Backend zwraca URL do manifestu HLS.
     * 9. Jeśli film ma DRM, backend zwraca też licenseUrl i playbackToken.
     *
     * Po tej odpowiedzi player powinien pobierać manifest i segmenty
     * bezpośrednio z CDN/storage, a nie przez backend aplikacyjny.
     */
    @GetMapping("/videos/{videoId}")
    public PlaybackResponse playback(@AuthenticationPrincipal User user,
                                     @PathVariable UUID videoId,
                                     HttpServletRequest request,
                                     HttpServletResponse response) throws Exception {
        /*
         * Pobieramy film po ID.
         *
         * Jeśli film nie istnieje, zwracamy 404.
         */
        Video video = videos.findById(videoId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Video not found"
                ));

        /*
         * Film musi być opublikowany i mieć gotowy HLS master manifest.
         *
         * PUBLISHED oznacza, że film jest widoczny dla użytkowników.
         * hlsMasterObjectKey oznacza, że transkodowanie zakończyło się sukcesem
         * i istnieje manifest potrzebny playerowi.
         */
        if (video.getStatus() != VideoStatus.PUBLISHED || video.getHlsMasterObjectKey() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Video is not ready for playback"
            );
        }

        /*
         * Rozpoznajemy kraj użytkownika z requestu.
         *
         * W praktyce zwykle pochodzi to z nagłówków ustawionych przez CDN,
         * np. CloudFront-Viewer-Country albo CF-IPCountry.
         */
        String country = geo.resolveCountry(request);

        /*
         * Sprawdzamy, czy film jest dostępny w kraju użytkownika.
         *
         * Wynik geoAllowed przekazujemy dalej do EntitlementService,
         * żeby decyzja dostępu była jedna i spójna.
         */
        boolean geoAllowed = geo.isAllowed(video, country);

        /*
         * Sprawdzamy prawo użytkownika do odtworzenia filmu.
         *
         * Decyzja może obejmować:
         * - plan subskrypcji użytkownika,
         * - minimalny wymagany plan filmu,
         * - geo-blocking,
         * - status konta lub inne reguły premium.
         */
        var decision = entitlements.check(user, video, geoAllowed);

        if (!decision.allowed()) {
            playbackDenied.increment();

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    decision.reason()
            );
        }

        /*
         * TTL dostępu playbackowego.
         *
         * Przez ten czas signed cookies i playback token pozostają ważne.
         * Krótki TTL ogranicza ryzyko nadużyć po wycieku linku/tokenu.
         */
        Duration ttl = Duration.ofHours(1);

        /*
         * Ustawiamy signed cookies w odpowiedzi.
         *
         * CDN może używać tych cookies do autoryzacji pobierania manifestów
         * i segmentów, bez pytania backendu o każdy plik.
         *
         * To kluczowe, bo segmentów wideo może być bardzo dużo.
         */
        for (ResponseCookie cookie : signedCookies.createPlaybackCookies(user, video, ttl)) {
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }

        playbackStarted.increment();

        /*
         * Tymczasowy signed URL do manifestu HLS.
         *
         * W MVP jest to wygodne, bo player może od razu pobrać master.m3u8.
         * Równolegle zwracamy też CDN URL, który reprezentuje docelowy model
         * dostarczania assetów przez CDN.
         */
        String signedUrl = storage.presignedGetUrl(video.getHlsMasterObjectKey(), 60);

        /*
         * Jeśli film jest DRM protected, generujemy playback token.
         *
         * Ten token zostanie później wysłany przez player do /api/drm/license.
         * License endpoint zweryfikuje, czy token pasuje do usera i video.
         */
        String playbackToken = video.isDrmProtected()
                ? signedCookies.createPlaybackToken(user, video, ttl)
                : null;

        /*
         * License URL jest zwracany tylko dla filmów DRM.
         *
         * Dla zwykłych filmów player nie musi prosić o licencję.
         */
        String licenseUrl = video.isDrmProtected()
                ? "/api/drm/license"
                : null;

        return new PlaybackResponse(
                video.getId(),
                "HLS",
                signedUrl,
                storage.cdnUrl(video.getHlsMasterObjectKey()),
                Instant.now().plus(ttl),
                video.isDrmProtected(),
                licenseUrl,
                playbackToken,
                decision.requiredPlan(),
                decision.userPlan(),
                country,
                "VS-Policy/VS-Signature"
        );
    }
}
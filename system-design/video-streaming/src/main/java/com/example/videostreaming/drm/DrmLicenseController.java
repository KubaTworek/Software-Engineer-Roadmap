package com.example.videostreaming.drm;

import com.example.videostreaming.auth.User;
import com.example.videostreaming.catalog.Video;
import com.example.videostreaming.catalog.VideoRepository;
import com.example.videostreaming.geo.GeoService;
import com.example.videostreaming.premium.EntitlementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static com.example.videostreaming.drm.DrmDtos.*;

/**
 * Kontroler mock DRM license servera.
 *
 * Główna odpowiedzialność:
 * - przyjmuje request o licencję DRM od playera,
 * - sprawdza, czy użytkownik ma prawo oglądać film,
 * - sprawdza geo-blocking,
 * - sprawdza ważność playback tokenu,
 * - zwraca mock licencję DRM.
 *
 * Ważne:
 * To nie jest prawdziwy Widevine/FairPlay/PlayReady license server.
 * To developerski mock pokazujący, gdzie w architekturze powinien odbywać się
 * check entitlementów i wydawanie licencji.
 */
@RestController
@RequestMapping("/api/drm")
public class DrmLicenseController {

    /**
     * Repozytorium filmów.
     *
     * Używane do pobrania filmu, którego dotyczy request o licencję.
     * Film zawiera informacje potrzebne do decyzji DRM:
     * - czy jest DRM protected,
     * - jaka jest polityka licencji,
     * - jakie są ograniczenia premium/geo.
     */
    private final VideoRepository videos;

    /**
     * Serwis entitlementów.
     *
     * Sprawdza, czy użytkownik ma prawo obejrzeć dany film,
     * np. na podstawie planu subskrypcji, statusu konta i dostępności treści.
     */
    private final EntitlementService entitlements;

    /**
     * Serwis geo-blockingu.
     *
     * Rozpoznaje kraj użytkownika na podstawie requestu
     * i sprawdza, czy film jest dostępny w tym kraju.
     */
    private final GeoService geo;

    /**
     * Serwis podpisanych tokenów/cookies.
     *
     * Tutaj używany do weryfikacji playback tokenu.
     * Token wiąże request o licencję z konkretnym użytkownikiem i filmem.
     */
    private final SignedCookieService signedCookies;

    public DrmLicenseController(VideoRepository videos,
                                EntitlementService entitlements,
                                GeoService geo,
                                SignedCookieService signedCookies) {
        this.videos = videos;
        this.entitlements = entitlements;
        this.geo = geo;
        this.signedCookies = signedCookies;
    }

    /**
     * Wydaje mock licencję DRM dla playera.
     *
     * Typowy flow:
     * 1. Player dostaje playback info z PlaybackService.
     * 2. Jeśli film jest DRM protected, player woła ten endpoint.
     * 3. Backend pobiera film.
     * 4. Backend sprawdza geo-blocking.
     * 5. Backend sprawdza entitlement użytkownika.
     * 6. Backend sprawdza playback token.
     * 7. Backend zwraca licencję DRM.
     *
     * Kolejność checków ma znaczenie:
     * - najpierw sprawdzamy, czy film istnieje,
     * - potem czy user ma prawo do treści,
     * - potem czy film faktycznie wymaga DRM,
     * - na końcu czy token playbacku jest ważny dla tego usera i filmu.
     */
    @PostMapping("/license")
    public LicenseResponse license(@AuthenticationPrincipal User user,
                                   @Valid @RequestBody LicenseRequest request,
                                   HttpServletRequest httpRequest) {
        /*
         * Pobieramy film, dla którego player żąda licencji.
         *
         * Jeśli film nie istnieje, zwracamy 404.
         */
        Video video = videos.findById(request.videoId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Video not found"
                ));

        /*
         * Ustalamy kraj użytkownika i sprawdzamy geo-blocking.
         *
         * GeoService może bazować np. na nagłówkach CDN:
         * - CloudFront-Viewer-Country,
         * - CF-IPCountry,
         * - X-Geo-Country.
         */
        boolean geoAllowed = geo.isAllowed(
                video,
                geo.resolveCountry(httpRequest)
        );

        /*
         * Sprawdzamy entitlement użytkownika do filmu.
         *
         * Decyzja może uwzględniać:
         * - plan subskrypcji,
         * - status konta,
         * - politykę premium filmu,
         * - geoAllowed.
         *
         * Jeśli decyzja jest negatywna, nie wydajemy licencji.
         */
        var decision = entitlements.check(user, video, geoAllowed);

        if (!decision.allowed()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    decision.reason()
            );
        }

        /*
         * Ten endpoint ma sens tylko dla filmów zabezpieczonych DRM.
         *
         * Jeśli film nie wymaga DRM, request o licencję oznacza konflikt
         * z konfiguracją materiału albo błędne zachowanie playera.
         */
        if (!video.isDrmProtected()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Video is not DRM protected"
            );
        }

        /*
         * Weryfikujemy playback token.
         *
         * Token musi pasować do:
         * - aktualnego userId,
         * - videoId,
         * - podpisu/czasu ważności.
         *
         * To chroni przed sytuacją, gdzie ktoś próbuje użyć tokenu
         * wydanego dla innego filmu albo innego użytkownika.
         */
        boolean tokenValid = signedCookies.verifyPlaybackToken(
                request.playbackToken(),
                user.getId().toString(),
                video.getId().toString()
        );

        if (!tokenValid) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Invalid playback token"
            );
        }

        /*
         * Generujemy mock licencję.
         *
         * To tylko placeholder developerski.
         * Produkcyjnie tutaj nastąpiłoby wywołanie prawdziwego DRM providera
         * albo wygenerowanie licencji Widevine/FairPlay/PlayReady.
         */
        String mockLicense = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        (
                                "mock-license:"
                                        + request.drmSystem()
                                        + ":"
                                        + video.getId()
                                        + ":"
                                        + user.getId()
                        ).getBytes(StandardCharsets.UTF_8)
                );

        /*
         * Zwracamy licencję z polityką i czasem ważności.
         *
         * licensePolicy może określać np.:
         * - streaming only,
         * - offline allowed,
         * - rental window,
         * - ograniczenia urządzeń.
         *
         * W MVP jest to tylko wartość opisowa.
         */
        return new LicenseResponse(
                video.getId(),
                request.drmSystem(),
                mockLicense,
                video.getLicensePolicy(),
                Instant.now().plusSeconds(3600)
        );
    }
}
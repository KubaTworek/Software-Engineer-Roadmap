package com.example.videostreaming.catalog;

import com.example.videostreaming.auth.User;
import com.example.videostreaming.search.SearchService;
import com.example.videostreaming.premium.PremiumDtos.UpdateVideoPremiumPolicyRequest;
import jakarta.validation.Valid;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static com.example.videostreaming.catalog.CatalogDtos.*;

/**
 * Główny kontroler katalogu VOD.
 *
 * Odpowiada za:
 * - publiczne pobieranie opublikowanych filmów,
 * - pobieranie szczegółów filmu,
 * - administracyjne tworzenie i edycję metadanych,
 * - ustawianie polityki premium,
 * - publikowanie filmu,
 * - synchronizację danych katalogu z wyszukiwarką,
 * - czyszczenie cache po zmianach w katalogu.
 *
 * Ważne:
 * Ten kontroler NIE obsługuje uploadu plików, transkodowania ani playbacku.
 * Zarządza tylko metadanymi filmu i jego statusem w katalogu.
 */
@RestController
@RequestMapping("/api/videos")
public class CatalogController {

    /**
     * Repozytorium filmów.
     *
     * Jest głównym źródłem prawdy dla metadanych filmu:
     * tytułu, opisu, statusu, widoczności, polityki premium itd.
     */
    private final VideoRepository videos;

    /**
     * Serwis wyszukiwania.
     *
     * Po zmianach opublikowanego filmu aktualizuje indeks search engine,
     * żeby użytkownicy mogli znaleźć aktualną wersję filmu.
     */
    private final SearchService search;

    public CatalogController(VideoRepository videos, SearchService search) {
        this.videos = videos;
        this.search = search;
    }

    /**
     * Zwraca publiczną listę opublikowanych filmów.
     *
     * Endpoint używany przez homepage, katalog, listy filmów itp.
     *
     * Zwracane są wyłącznie filmy:
     * - ze statusem PUBLISHED,
     * - z widocznością PUBLIC.
     *
     * Cache:
     * Wynik jest cache'owany per kombinacja page:size, bo lista publicznych filmów
     * jest często czytana i rzadziej zmieniana.
     *
     * Ograniczenie:
     * Maksymalny rozmiar strony to 100, żeby uniknąć zbyt ciężkich zapytań.
     */
    @GetMapping
    @Cacheable(cacheNames = "publishedVideos", key = "#page + ':' + #size")
    public Page<VideoResponse> published(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return videos.findByStatusAndVisibilityOrderByPublishedAtDesc(
                VideoStatus.PUBLISHED,
                VideoVisibility.PUBLIC,
                PageRequest.of(page, Math.min(size, 100))
        ).map(VideoResponse::from);
    }

    /**
     * Zwraca szczegóły pojedynczego filmu.
     *
     * Endpoint publiczny — dlatego ukrywa każdy film, który nie jest gotowy
     * do publicznego oglądania.
     *
     * Celowe zachowanie:
     * Dla filmu nieopublikowanego albo prywatnego zwracamy 404, a nie 403.
     * Dzięki temu nie ujawniamy istnienia ukrytych lub roboczych materiałów.
     *
     * Cache:
     * Szczegóły filmu są cache'owane po ID, bo są często pobierane przed playbackiem.
     */
    @GetMapping("/{id}")
    @Cacheable(cacheNames = "videoDetails", key = "#id")
    public VideoResponse get(@PathVariable UUID id) {
        Video video = videos.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found"));

        if (video.getStatus() != VideoStatus.PUBLISHED || video.getVisibility() != VideoVisibility.PUBLIC) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found");
        }

        return VideoResponse.from(video);
    }

    /**
     * Tworzy nowy rekord filmu w katalogu.
     *
     * Dostęp:
     * Tylko ADMIN może tworzyć filmy.
     *
     * Ten endpoint tworzy wyłącznie metadane filmu.
     * Sam plik wideo jest dodawany osobnym flow uploadu przez signed URL.
     *
     * Po utworzeniu filmu czyszczone są cache'e katalogu i wyszukiwania,
     * bo nowy film może później wpłynąć na listy lub search.
     *
     * @param request dane wejściowe: tytuł i opis
     * @param user aktualnie zalogowany admin, ustawiany jako właściciel/creator filmu
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @CacheEvict(cacheNames = {"publishedVideos", "videoDetails", "search"}, allEntries = true)
    public VideoResponse create(@Valid @RequestBody CreateVideoRequest request,
                                @AuthenticationPrincipal User user) {
        return VideoResponse.from(
                videos.save(new Video(request.title(), request.description(), user))
        );
    }

    /**
     * Aktualizuje podstawowe metadane filmu.
     *
     * Dostęp:
     * Tylko ADMIN.
     *
     * Aktualizowane są dane katalogowe, np. tytuł i opis.
     *
     * Jeżeli film jest już opublikowany, aktualizujemy również indeks search engine,
     * żeby wyszukiwarka nie zwracała starego tytułu/opisu.
     *
     * Cache:
     * Po zmianie metadanych czyszczone są:
     * - lista opublikowanych filmów,
     * - szczegóły filmów,
     * - cache wyszukiwania.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @CacheEvict(cacheNames = {"publishedVideos", "videoDetails", "search"}, allEntries = true)
    public VideoResponse update(@PathVariable UUID id,
                                @Valid @RequestBody UpdateVideoRequest request) {
        Video video = videos.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found"));

        video.updateMetadata(request.title(), request.description());

        Video saved = videos.save(video);

        if (saved.getStatus() == VideoStatus.PUBLISHED) {
            search.index(saved);
        }

        return VideoResponse.from(saved);
    }

    /**
     * Aktualizuje politykę premium filmu.
     *
     * Dostęp:
     * Tylko ADMIN.
     *
     * Ten endpoint wpływa bezpośrednio na możliwość odtworzenia filmu.
     * Zmieniane są m.in.:
     * - minimalny wymagany plan subskrypcji,
     * - lista dozwolonych krajów,
     * - flaga DRM,
     * - polityka licencji DRM.
     *
     * Cache:
     * Oprócz cache katalogu i search czyszczony jest też cache playbacku,
     * bo zmiana polityki premium może natychmiast zmienić to,
     * czy użytkownik ma prawo odtworzyć film.
     *
     * Jeżeli film jest opublikowany, aktualizujemy search index,
     * żeby wyszukiwarka miała aktualne dane o dostępności/polityce filmu.
     */
    @PutMapping("/{id}/premium-policy")
    @PreAuthorize("hasRole('ADMIN')")
    @CacheEvict(cacheNames = {"publishedVideos", "videoDetails", "search", "playback"}, allEntries = true)
    public VideoResponse updatePremiumPolicy(@PathVariable UUID id,
                                             @RequestBody UpdateVideoPremiumPolicyRequest request) {
        Video video = videos.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found"));

        video.updatePremiumPolicy(
                request.minimumPlanCode(),
                request.allowedCountries(),
                request.drmProtected(),
                request.licensePolicy()
        );

        Video saved = videos.save(video);

        if (saved.getStatus() == VideoStatus.PUBLISHED) {
            search.index(saved);
        }

        return VideoResponse.from(saved);
    }

    /**
     * Publikuje film w katalogu.
     *
     * Dostęp:
     * Tylko ADMIN.
     *
     * Film można opublikować tylko wtedy, gdy:
     * - jest READY, czyli przeszedł upload/transkodowanie i ma gotowe assety,
     * - albo jest już PUBLISHED, wtedy operacja jest idempotentna.
     *
     * Jeśli film nie jest READY, zwracamy 409 CONFLICT.
     * To chroni system przed pokazaniem użytkownikom filmu,
     * którego nie da się jeszcze odtworzyć.
     *
     * Po publikacji:
     * - zapisujemy status PUBLISHED,
     * - indeksujemy film w search engine,
     * - czyścimy cache katalogu i wyszukiwania.
     */
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    @CacheEvict(cacheNames = {"publishedVideos", "videoDetails", "search"}, allEntries = true)
    public VideoResponse publish(@PathVariable UUID id) {
        Video video = videos.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found"));

        if (video.getStatus() != VideoStatus.READY && video.getStatus() != VideoStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Video must be READY before publishing");
        }

        video.publish();

        Video saved = videos.save(video);

        search.index(saved);

        return VideoResponse.from(saved);
    }
}
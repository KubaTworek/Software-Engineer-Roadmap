package com.example.videostreaming.cdn;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Administracyjny kontroler do operacji CDN.
 *
 * W tej klasie obsługujemy ręczny pre-warming CDN,
 * czyli wcześniejsze pobranie manifestu i segmentów wideo,
 * zanim zrobią to użytkownicy.
 *
 * Cel:
 * - zmniejszyć ryzyko wolnego pierwszego odtworzenia,
 * - ograniczyć nagły load na origin storage,
 * - przygotować CDN przed premierą popularnego materiału.
 *
 * Ważne:
 * Ten endpoint jest dostępny tylko dla ADMIN.
 */
@RestController
@RequestMapping("/api/admin/cdn")
@PreAuthorize("hasRole('ADMIN')")
public class CdnAdminController {

    /**
     * Serwis wykonujący właściwy pre-warming.
     *
     * Kontroler nie wie, jak CDN jest rozgrzewany.
     * Może to być:
     * - zwykłe HTTP GET do manifestu i segmentów,
     * - wywołanie API konkretnego CDN providera,
     * - enqueue joba do osobnej kolejki pre-warmingu.
     */
    private final CdnPrewarmService prewarmService;

    public CdnAdminController(CdnPrewarmService prewarmService) {
        this.prewarmService = prewarmService;
    }

    /**
     * Uruchamia pre-warming CDN dla konkretnego filmu.
     *
     * Typowy scenariusz:
     * - film ma się zaraz pojawić na stronie głównej,
     * - film będzie promowany,
     * - wiadomo, że po publikacji pojawi się duży ruch,
     * - admin chce wcześniej zapełnić cache CDN.
     *
     * Flow:
     * 1. Admin wywołuje endpoint z videoId.
     * 2. Serwis znajduje manifest i segmenty filmu.
     * 3. Serwis wykonuje pre-warming dla wygenerowanych URL-i.
     * 4. API zwraca listę obiektów, dla których wysłano request.
     *
     * @param videoId film, którego assety HLS mają zostać rozgrzane w CDN
     * @return liczba obiektów i lista URL-i/rezultatów pre-warmingu
     */
    @PostMapping("/prewarm/{videoId}")
    public PrewarmResponse prewarm(@PathVariable UUID videoId) {
        List<String> urls = prewarmService.prewarm(videoId);

        return new PrewarmResponse(
                videoId,
                urls.size(),
                urls
        );
    }

    /**
     * Odpowiedź endpointu pre-warmingu.
     *
     * videoId:
     * - film, dla którego wykonano operację.
     *
     * requestedObjects:
     * - liczba obiektów zgłoszonych do pre-warmingu.
     *
     * results:
     * - lista URL-i albo wyników zwróconych przez CdnPrewarmService.
     *
     * Uwaga:
     * W tym MVP results jest proste i diagnostyczne.
     * Produkcyjnie lepiej zwracać status per obiekt:
     * SUCCESS / FAILED / SKIPPED + kod HTTP / komunikat błędu.
     */
    public record PrewarmResponse(
            UUID videoId,
            int requestedObjects,
            List<String> results
    ) {}
}
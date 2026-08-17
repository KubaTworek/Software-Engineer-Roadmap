package com.example.videostreaming.live;

import com.example.videostreaming.auth.User;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Kontroler transmisji live.
 *
 * Główna odpowiedzialność:
 * - wystawia API do zarządzania live streamami,
 * - pozwala użytkownikom pobierać listę i szczegóły transmisji,
 * - pozwala adminowi tworzyć, edytować, startować i zatrzymywać live,
 * - zwraca dane playbacku live,
 * - uruchamia konwersję zakończonego live do normalnego VOD.
 *
 * Ważne:
 * Ten kontroler nie wykonuje transkodowania live bezpośrednio.
 * Cała logika biznesowa i integracja z workerami znajduje się w LiveStreamService.
 */
@RestController
@RequestMapping("/api/live")
public class LiveStreamController {

    /**
     * Serwis domenowy live streamingu.
     *
     * Kontroler deleguje do niego całą logikę:
     * - statusy transmisji,
     * - generowanie stream key,
     * - start/stop,
     * - playback URL,
     * - live-to-VOD.
     */
    private final LiveStreamService service;

    public LiveStreamController(LiveStreamService service) {
        this.service = service;
    }

    /**
     * Zwraca listę transmisji live.
     *
     * Endpoint publiczny — może być używany np. przez:
     * - stronę z aktualnymi transmisjami,
     * - sekcję "Live now",
     * - panel z zaplanowanymi wydarzeniami.
     *
     * Parametry:
     * - status: opcjonalny filtr, np. LIVE, SCHEDULED, ENDED,
     * - page/size: paginacja wyników.
     *
     * Wyniki są mapowane z encji domenowej na DTO odpowiedzi,
     * żeby nie wystawiać bezpośrednio struktury bazy danych.
     */
    @GetMapping
    public Page<LiveDtos.LiveStreamResponse> list(@RequestParam(required = false) LiveStatus status,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return service.list(status, page, size)
                .map(service::toResponse);
    }

    /**
     * Zwraca szczegóły jednej transmisji live.
     *
     * Endpoint publiczny.
     *
     * Używany np. przed wejściem na stronę wydarzenia live,
     * żeby pobrać tytuł, opis, status, planowany start,
     * tryb low-latency i inne metadane.
     */
    @GetMapping("/{id}")
    public LiveDtos.LiveStreamResponse get(@PathVariable UUID id) {
        return service.toResponse(service.get(id));
    }

    /**
     * Tworzy nową transmisję live.
     *
     * Dostęp:
     * Tylko ADMIN.
     *
     * Typowy efekt:
     * - powstaje rekord live streamu,
     * - generowany jest stream key,
     * - transmisja zwykle startuje jako SCHEDULED,
     * - admin może przekazać stream key do encodera/OBS/FFmpeg.
     *
     * user pochodzi z AuthenticationPrincipal i wskazuje admina,
     * który utworzył transmisję.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public LiveDtos.LiveStreamResponse create(@Valid @RequestBody LiveDtos.CreateLiveRequest request,
                                              @AuthenticationPrincipal User user) {
        return service.toResponse(service.create(request, user));
    }

    /**
     * Aktualizuje metadane transmisji live.
     *
     * Dostęp:
     * Tylko ADMIN.
     *
     * Używane do zmiany np.:
     * - tytułu,
     * - opisu,
     * - planowanego czasu startu,
     * - ustawień DVR,
     * - trybu low-latency.
     *
     * Sama aktualizacja metadanych nie oznacza jeszcze startu transmisji.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public LiveDtos.LiveStreamResponse update(@PathVariable UUID id,
                                              @Valid @RequestBody LiveDtos.UpdateLiveRequest request) {
        return service.toResponse(service.update(id, request));
    }

    /**
     * Uruchamia transmisję live.
     *
     * Dostęp:
     * Tylko ADMIN.
     *
     * Typowy flow:
     * 1. Admin wywołuje start.
     * 2. LiveStreamService zmienia status na STARTING/LIVE.
     * 3. System publikuje job/event do live workera.
     * 4. Worker uruchamia FFmpeg pipeline dla ingestu RTMP.
     * 5. Powstaje live HLS playlist dostępny dla widzów.
     *
     * Ten endpoint nie powinien blokować się na pełnym starcie FFmpeg.
     * Start pipeline'u live powinien być obsługiwany asynchronicznie.
     */
    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('ADMIN')")
    public LiveDtos.LiveStreamResponse start(@PathVariable UUID id) {
        return service.toResponse(service.start(id));
    }

    /**
     * Zatrzymuje transmisję live.
     *
     * Dostęp:
     * Tylko ADMIN.
     *
     * Typowy flow:
     * - zatrzymanie pipeline'u live,
     * - zamknięcie playlisty HLS,
     * - ustawienie statusu ENDED,
     * - zachowanie nagrania, jeśli live-to-VOD jest obsługiwane.
     *
     * Stop również powinien być operacją bezpieczną przy ponownym wywołaniu,
     * bo admin albo system może wysłać ją więcej niż raz.
     */
    @PostMapping("/{id}/stop")
    @PreAuthorize("hasRole('ADMIN')")
    public LiveDtos.LiveStreamResponse stop(@PathVariable UUID id) {
        return service.toResponse(service.stop(id));
    }

    /**
     * Zwraca dane potrzebne do odtwarzania transmisji live.
     *
     * Endpoint publiczny dla playera.
     *
     * Zwykle zwraca:
     * - URL do manifestu HLS live,
     * - status transmisji,
     * - informację o low-latency,
     * - ewentualnie DVR window.
     *
     * Ważne:
     * Kontrola dostępu, geo-blocking albo signed cookies powinny być
     * obsłużone w LiveStreamService lub warstwie playback/security,
     * nie bezpośrednio w kontrolerze.
     */
    @GetMapping("/{id}/playback")
    public LiveDtos.LivePlaybackResponse playback(@PathVariable UUID id) {
        return service.playback(id);
    }

    /**
     * Konwertuje zakończoną transmisję live do zwykłego VOD.
     *
     * Dostęp:
     * Tylko ADMIN.
     *
     * Typowy flow:
     * 1. Live musi być zakończony.
     * 2. System bierze nagranie/live archive.
     * 3. Tworzy nowy rekord Video w katalogu.
     * 4. Publikuje job do istniejącego pipeline'u VOD transkodowania.
     * 5. Po zakończeniu transkodowania materiał może być opublikowany jako VOD.
     *
     * user wskazuje admina, który tworzy materiał VOD z live'a.
     */
    @PostMapping("/{id}/convert-to-vod")
    @PreAuthorize("hasRole('ADMIN')")
    public LiveDtos.LiveToVodResponse convertToVod(@PathVariable UUID id,
                                                   @AuthenticationPrincipal User user) {
        return service.convertToVod(id, user);
    }
}
package com.example.videostreaming.watch;

import com.example.videostreaming.auth.User;
import com.example.videostreaming.catalog.VideoRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static com.example.videostreaming.watch.WatchDtos.*;

/**
 * Kontroler postępu oglądania filmu.
 *
 * Główna odpowiedzialność:
 * - zapisuje aktualną pozycję odtwarzania użytkownika,
 * - zwraca ostatnio zapisaną pozycję dla danego filmu,
 * - pozwala aplikacji kontynuować oglądanie od miejsca przerwania.
 *
 * Typowy flow:
 * 1. Player cyklicznie wysyła aktualny czas odtwarzania.
 * 2. Backend zapisuje progress per user + video.
 * 3. Po ponownym wejściu na film aplikacja pobiera progress.
 * 4. Player może wznowić odtwarzanie od zapisanej pozycji.
 *
 * Ważne:
 * Progress jest przypisany do konkretnego użytkownika.
 * Użytkownik nie może odczytać ani nadpisać postępu innego konta,
 * bo userId pochodzi z aktualnego AuthenticationPrincipal.
 */
@RestController
@RequestMapping("/api/watch-progress")
public class WatchProgressController {

    /**
     * Repozytorium postępu oglądania.
     *
     * Przechowuje rekordy identyfikowane parą:
     * - userId,
     * - videoId.
     *
     * Dzięki temu jeden użytkownik ma maksymalnie jeden rekord progressu
     * dla jednego filmu.
     */
    private final WatchProgressRepository progress;

    /**
     * Repozytorium filmów.
     *
     * Używane tutaj tylko do sprawdzenia, czy film istnieje,
     * zanim zapiszemy dla niego progress.
     */
    private final VideoRepository videos;

    public WatchProgressController(WatchProgressRepository progress, VideoRepository videos) {
        this.progress = progress;
        this.videos = videos;
    }

    /**
     * Zapisuje albo aktualizuje postęp oglądania filmu.
     *
     * Endpoint wywoływany przez player, np. co kilka lub kilkanaście sekund,
     * oraz przy pauzie, zamknięciu strony albo zakończeniu odtwarzania.
     *
     * Flow:
     * 1. Sprawdza, czy film istnieje.
     * 2. Buduje klucz userId + videoId.
     * 3. Szuka istniejącego progressu.
     * 4. Jeśli go nie ma, tworzy nowy rekord.
     * 5. Aktualizuje positionSeconds i durationSeconds.
     * 6. Zapisuje wynik w bazie.
     *
     * @param videoId film, którego dotyczy progress
     * @param request aktualna pozycja i długość materiału
     * @param user aktualnie zalogowany użytkownik
     */
    @PutMapping("/{videoId}")
    public ProgressResponse save(@PathVariable UUID videoId,
                                 @Valid @RequestBody SaveProgressRequest request,
                                 @AuthenticationPrincipal User user) {
        /*
         * Nie zapisujemy progressu dla nieistniejącego filmu.
         *
         * To chroni bazę przed śmieciowymi rekordami,
         * np. jeśli klient wyśle błędne videoId.
         */
        if (!videos.existsById(videoId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found");
        }

        /*
         * Klucz progressu składa się z userId i videoId.
         *
         * To najważniejsza reguła tej klasy:
         * progress jest prywatny dla użytkownika i konkretnego filmu.
         */
        WatchProgressId id = new WatchProgressId(user.getId(), videoId);

        /*
         * Jeśli rekord już istnieje, aktualizujemy go.
         * Jeśli nie istnieje, tworzymy nowy.
         *
         * Dzięki temu endpoint PUT jest praktycznie idempotentny:
         * wielokrotne wysłanie progressu dla tego samego filmu
         * nie tworzy duplikatów.
         */
        WatchProgress entity = progress.findById(id)
                .orElseGet(() -> new WatchProgress(
                        user.getId(),
                        videoId,
                        request.positionSeconds(),
                        request.durationSeconds()
                ));

        /*
         * Aktualizujemy pozycję odtwarzania.
         *
         * positionSeconds mówi, gdzie użytkownik skończył oglądać.
         * durationSeconds pozwala później policzyć procent obejrzenia
         * albo zdecydować, czy film traktować jako zakończony.
         */
        entity.update(request.positionSeconds(), request.durationSeconds());

        return ProgressResponse.from(progress.save(entity));
    }

    /**
     * Pobiera ostatnio zapisaną pozycję oglądania dla filmu.
     *
     * Endpoint używany przed startem playera.
     * Jeśli progress istnieje, klient może wznowić odtwarzanie
     * od zapisanej pozycji.
     *
     * Jeśli progress nie istnieje, zwracane jest 404.
     * Klient może wtedy rozpocząć film od początku.
     *
     * @param videoId film, dla którego pobieramy progress
     * @param user aktualnie zalogowany użytkownik
     */
    @GetMapping("/{videoId}")
    public ProgressResponse get(@PathVariable UUID videoId,
                                @AuthenticationPrincipal User user) {
        /*
         * Szukamy progressu wyłącznie dla aktualnie zalogowanego użytkownika.
         *
         * userId nie jest przyjmowane z requestu, więc klient nie może
         * podejrzeć progressu innego użytkownika.
         */
        WatchProgressId id = new WatchProgressId(user.getId(), videoId);

        return progress.findById(id)
                .map(ProgressResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Progress not found"
                ));
    }
}
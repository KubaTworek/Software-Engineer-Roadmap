package com.example.newsfeed.follow;

import com.example.newsfeed.auth.CurrentUser;
import com.example.newsfeed.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Kontroler HTTP odpowiedzialny za relacje follow/unfollow między użytkownikami.
 *
 * To jest warstwa API:
 * - pozwala zalogowanemu użytkownikowi obserwować innego użytkownika,
 * - pozwala przestać obserwować użytkownika,
 * - zwraca statystyki obserwujących i obserwowanych.
 *
 * Kontroler nie zawiera logiki biznesowej.
 * Nie sprawdza samodzielnie, czy użytkownik istnieje, czy ktoś próbuje
 * obserwować samego siebie ani czy relacja follow już istnieje.
 *
 * Te zasady są w FollowService.
 */
@RestController
@RequestMapping("/api/v1")
public class FollowController {

    /**
     * Serwis biznesowy obsługujący relacje follow.
     *
     * FollowService odpowiada za:
     * - sprawdzenie, czy followee istnieje,
     * - blokadę follow samego siebie,
     * - zapis relacji follower -> followee,
     * - usunięcie relacji,
     * - aktualizację shardów followersów,
     * - backfill feedu,
     * - czyszczenie cache feedu,
     * - publikację eventów follow.created / follow.deleted.
     */
    private final FollowService followService;

    /**
     * Wstrzyknięcie FollowService przez konstruktor.
     *
     * Dzięki temu kontroler jest prosty i deleguje całą logikę relacji
     * użytkowników do warstwy serwisowej.
     */
    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    /**
     * Tworzy relację obserwowania.
     *
     * Endpoint:
     * POST /api/v1/users/{userId}/follow
     *
     * currentUser to użytkownik, który wykonuje akcję follow.
     * userId z URL to użytkownik, który ma zostać zaobserwowany.
     *
     * Przykład:
     * currentUser = Anna
     * userId = Jan
     *
     * Oznacza:
     * Anna zaczyna obserwować Jana.
     *
     * Zwraca HTTP 201 CREATED, bo powstaje nowy zasób:
     * relacja follow między dwoma użytkownikami.
     */
    @PostMapping("/users/{userId}/follow")
    @ResponseStatus(HttpStatus.CREATED)
    public FollowResponse follow(
            @CurrentUser User currentUser,
            @PathVariable UUID userId
    ) {
        /*
         * Kontroler nie przekazuje followerId z body.
         *
         * Followerem zawsze jest aktualnie zalogowany użytkownik.
         * To chroni przed sytuacją, w której klient próbuje utworzyć relację
         * follow w imieniu innego użytkownika.
         */
        return followService.follow(currentUser, userId);
    }

    /**
     * Usuwa relację obserwowania.
     *
     * Endpoint:
     * DELETE /api/v1/users/{userId}/follow
     *
     * currentUser to użytkownik, który przestaje obserwować.
     * userId to użytkownik, który ma zostać odobserwowany.
     *
     * Przykład:
     * currentUser = Anna
     * userId = Jan
     *
     * Oznacza:
     * Anna przestaje obserwować Jana.
     *
     * Zwraca HTTP 204 NO CONTENT, bo operacja nie musi zwracać body.
     */
    @DeleteMapping("/users/{userId}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(
            @CurrentUser User currentUser,
            @PathVariable UUID userId
    ) {
        /*
         * Sama operacja usuwania relacji jest delegowana do FollowService.
         *
         * To serwis decyduje, czy unfollow jest idempotentny,
         * czy brak relacji powinien być błędem, oraz jakie eventy/cache
         * trzeba zaktualizować.
         */
        followService.unfollow(currentUser, userId);
    }

    /**
     * Pobiera statystyki follow dla użytkownika.
     *
     * Endpoint:
     * GET /api/v1/users/{userId}/follow-stats
     *
     * Zwraca zwykle:
     * - liczbę użytkowników, których user obserwuje,
     * - liczbę użytkowników, którzy obserwują usera.
     *
     * Ten endpoint może być publiczny, bo nie zmienia stanu systemu
     * i nie wymaga aktualnego użytkownika.
     */
    @GetMapping("/users/{userId}/follow-stats")
    public FollowStatsResponse getStats(@PathVariable UUID userId) {
        /*
         * FollowService powinien sprawdzić, czy użytkownik istnieje,
         * a następnie policzyć following/followers.
         *
         * W dużej skali te wartości nie powinny być liczone COUNT-em
         * za każdym razem, tylko czytane z gotowej projekcji/licznika.
         */
        return followService.getStats(userId);
    }
}
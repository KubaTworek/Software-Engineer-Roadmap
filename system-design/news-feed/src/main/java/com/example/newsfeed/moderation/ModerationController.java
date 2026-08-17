package com.example.newsfeed.moderation;

import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Kontroler HTTP do obsługi panelu moderacji.
 *
 * Udostępnia endpointy, które pozwalają moderatorowi albo systemowi adminowemu
 * podejrzeć treści wymagające ręcznej weryfikacji.
 *
 * W kontekście aplikacji News Feed moderacja jest ważna, bo posty oznaczone
 * jako podejrzane nie powinny automatycznie trafiać do feedów użytkowników
 * bez kontroli.
 *
 * Ten kontroler jest tylko warstwą API.
 * Nie podejmuje decyzji moderacyjnych samodzielnie.
 * Całą logikę deleguje do ModerationService.
 */
@RestController
@RequestMapping("/api/v1/moderation")
public class ModerationController {

    /**
     * Serwis moderacji.
     *
     * Odpowiada za pobranie kolejki zgłoszeń / treści,
     * które wymagają sprawdzenia przez moderatora.
     */
    private final ModerationService moderationService;

    /**
     * Wstrzyknięcie serwisu moderacji.
     */
    public ModerationController(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    /**
     * Zwraca kolejkę elementów oczekujących na moderację.
     *
     * Endpoint:
     * GET /api/v1/moderation/queue
     *
     * Typowy przypadek użycia:
     * - post został oznaczony przez automatyczną moderację jako needs_review,
     * - system zapisuje ModerationReview,
     * - moderator otwiera kolejkę,
     * - UI pobiera listę elementów z tego endpointu.
     *
     * Kontroler nie filtruje i nie sortuje wyników samodzielnie.
     * To powinno być odpowiedzialnością ModerationService / repository.
     */
    @GetMapping("/queue")
    public List<ModerationReview> queue() {
        /*
         * Delegujemy pobranie kolejki do serwisu.
         *
         * Dzięki temu kontroler pozostaje cienki:
         * przyjmuje request HTTP i zwraca response,
         * ale nie zawiera logiki biznesowej moderacji.
         */
        return moderationService.queue();
    }
}
package com.example.newsfeed.moderation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Serwis odpowiedzialny za automatyczną moderację treści.
 *
 * W kontekście aplikacji News Feed ta klasa decyduje,
 * czy nowa treść może zostać opublikowana od razu,
 * czy wymaga ręcznej weryfikacji,
 * czy powinna zostać automatycznie ukryta.
 *
 * Typowy flow:
 * 1. użytkownik tworzy post,
 * 2. PostService wysyła tekst do ModerationService,
 * 3. ModerationService zwraca decyzję,
 * 4. PostService decyduje, czy publikować post do feedu,
 * 5. jeśli treść jest ryzykowna, powstaje ModerationReview.
 *
 * To jest uproszczona implementacja regułowa.
 * Produkcyjnie ten serwis zwykle korzystałby z modelu ML,
 * klasyfikatora bezpieczeństwa albo zewnętrznego systemu Trust & Safety.
 */
@Service
public class ModerationService {

    /**
     * Repozytorium zgłoszeń moderacyjnych.
     *
     * Przechowuje treści, które wymagają ręcznej weryfikacji
     * albo zostały automatycznie ukryte.
     */
    private final ModerationReviewRepository repository;

    /**
     * Próg automatycznego ukrycia treści.
     *
     * Jeśli score ryzyka przekroczy tę wartość,
     * treść dostaje status auto_hidden.
     *
     * Konfiguracja:
     *
     * newsfeed:
     *   moderation:
     *     auto-hide-threshold: 0.85
     */
    private final double autoHideThreshold;

    /**
     * Próg skierowania treści do ręcznej moderacji.
     *
     * Jeśli score przekroczy tę wartość,
     * ale nie przekroczy autoHideThreshold,
     * treść dostaje status needs_review.
     *
     * Konfiguracja:
     *
     * newsfeed:
     *   moderation:
     *     review-threshold: 0.60
     */
    private final double reviewThreshold;

    /**
     * Wstrzyknięcie repozytorium i progów moderacji z konfiguracji.
     *
     * Dzięki progom z application.yml można dostrajać agresywność moderacji
     * bez zmiany kodu.
     */
    public ModerationService(
            ModerationReviewRepository repository,
            @Value("${newsfeed.moderation.auto-hide-threshold:0.85}") double autoHideThreshold,
            @Value("${newsfeed.moderation.review-threshold:0.60}") double reviewThreshold
    ) {
        this.repository = repository;
        this.autoHideThreshold = autoHideThreshold;
        this.reviewThreshold = reviewThreshold;
    }

    /**
     * Ocenia tekst i zwraca decyzję moderacyjną.
     *
     * Metoda nie zapisuje nic do bazy.
     * Tylko liczy score i klasyfikuje treść.
     *
     * Możliwe statusy:
     * - approved: treść może przejść dalej,
     * - needs_review: treść wymaga ręcznej weryfikacji,
     * - auto_hidden: treść jest automatycznie ukrywana.
     *
     * W tej wersji score jest liczony prostymi regułami keywordowymi.
     */
    public ModerationDecision evaluateText(String text) {
        /*
         * Normalizujemy tekst do lowercase.
         *
         * Dzięki temu wykrywanie słów typu "spam" / "scam"
         * nie zależy od wielkości liter.
         *
         * Null traktujemy jako pusty tekst.
         */
        String lower = text == null
                ? ""
                : text.toLowerCase();

        /*
         * Score ryzyka.
         *
         * Im wyższy score, tym większe prawdopodobieństwo,
         * że treść jest spamem, scamem albo wymaga kontroli.
         */
        double score = 0.0;

        /*
         * Proste reguły keywordowe.
         *
         * To nie jest pełna moderacja NLP,
         * ale pokazuje mechanizm scoringu.
         */
        if (lower.contains("spam")) {
            score += 0.35;
        }

        if (lower.contains("scam")) {
            score += 0.35;
        }

        if (lower.contains("buy now")) {
            score += 0.20;
        }

        /*
         * Próba wykrycia agresywnego / spamowego stylu tekstu.
         *
         * Uwaga:
         * w tym kodzie sprawdzamy uppercase na zmiennej lower,
         * czyli po wcześniejszym toLowerCase().
         * To sprawia, że ta reguła praktycznie nigdy nie zadziała.
         *
         * Jeśli chcesz wykrywać caps lock, trzeba liczyć uppercase
         * na oryginalnym tekście, a nie na lower.
         */
        if (lower.length() > 0
                && lower.chars().filter(Character::isUpperCase).count() > lower.length() * 0.5) {
            score += 0.10;
        }

        /*
         * Najwyższy poziom ryzyka.
         *
         * Treść jest automatycznie ukrywana.
         * PostService nie powinien publikować jej do feedu.
         */
        if (score >= autoHideThreshold) {
            return new ModerationDecision(
                    "auto_hidden",
                    score,
                    "Automated high-risk classification"
            );
        }

        /*
         * Średni poziom ryzyka.
         *
         * Treść trafia do kolejki moderacyjnej,
         * ale decyzja może wymagać człowieka.
         */
        if (score >= reviewThreshold) {
            return new ModerationDecision(
                    "needs_review",
                    score,
                    "Automated medium-risk classification"
            );
        }

        /*
         * Niski poziom ryzyka.
         *
         * Treść przechodzi automatyczną moderację.
         */
        return new ModerationDecision(
                "approved",
                score,
                "No major automated risk"
        );
    }

    /**
     * Ocenia tekst i tworzy wpis do kolejki moderacyjnej, jeśli jest potrzebny.
     *
     * To jest metoda używana przez warstwę biznesową, np. PostService.
     *
     * Jeśli decyzja to:
     * - approved: nic nie zapisujemy,
     * - needs_review: tworzymy ModerationReview,
     * - auto_hidden: tworzymy ModerationReview.
     *
     * Zwracamy decyzję, żeby wywołujący mógł zdecydować,
     * czy post może być opublikowany do feedu.
     */
    @Transactional
    public ModerationDecision createReviewIfNeeded(
            String entityType,
            UUID entityId,
            String text
    ) {
        /*
         * Najpierw liczymy decyzję moderacyjną.
         */
        ModerationDecision decision = evaluateText(text);

        /*
         * Tylko ryzykowne treści trafiają do tabeli moderation_reviews.
         *
         * approved nie zapisujemy, żeby nie zaśmiecać kolejki moderacyjnej.
         */
        if (!"approved".equals(decision.status())) {
            repository.save(
                    new ModerationReview(
                            UUID.randomUUID(),
                            entityType,
                            entityId,
                            decision.status(),
                            decision.score(),
                            decision.reason(),
                            null,
                            Instant.now(),
                            null
                    )
            );
        }

        /*
         * Decyzja wraca do PostService / innego wywołującego.
         *
         * Dzięki temu logika publikacji może np.:
         * - zatrzymać auto_hidden,
         * - oznaczyć needs_review,
         * - pozwolić approved przejść dalej.
         */
        return decision;
    }

    /**
     * Zwraca kolejkę treści oczekujących na ręczną moderację.
     *
     * Endpoint /api/v1/moderation/queue korzysta właśnie z tej metody.
     *
     * Pobieramy tylko status needs_review,
     * bo auto_hidden jest już automatycznie ukryte.
     */
    @Transactional(readOnly = true)
    public List<ModerationReview> queue() {
        /*
         * Pobieramy maksymalnie 50 najstarszych zgłoszeń do sprawdzenia.
         *
         * Kolejność ASC po createdAt oznacza FIFO:
         * starsze zgłoszenia są obsługiwane jako pierwsze.
         */
        return repository.findTop50ByStatusOrderByCreatedAtAsc(
                "needs_review"
        );
    }
}
package com.example.newsfeed.ranking;

import com.example.newsfeed.feature.*;
import com.example.newsfeed.post.Post;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Serwis odpowiedzialny za scoring kandydatów do feedu.
 *
 * To jest uproszczona implementacja Learning-to-Rank.
 *
 * W praktyce ta klasa decyduje, jak wysoko dany post powinien pojawić się
 * w feedzie użytkownika.
 *
 * FeedService dostarcza kandydatów z różnych źródeł:
 * - FOLLOWING,
 * - RECOMMENDED,
 * - TRENDING,
 * - SPONSORED,
 * - CELEBRITY.
 *
 * LearningToRankService przypisuje każdemu kandydatowi score.
 * Potem FeedService sortuje kandydatów malejąco po score.
 */
@Service
public class LearningToRankService {

    /**
     * Feature Store dostarczający cechy posta.
     *
     * Przykładowe cechy:
     * - qualityScore,
     * - spamScore,
     * - ctr24h,
     * - reportRate,
     * - inne sygnały rankingowe.
     *
     * W tej wersji ranking używa głównie cech posta.
     * W produkcji powinien również używać cech użytkownika i relacji user-post.
     */
    private final FeatureStoreService featureStoreService;

    /**
     * Wstrzyknięcie FeatureStoreService.
     *
     * Ranking nie powinien sam liczyć wszystkich cech.
     * Powinien je pobierać z Feature Store, bo feature’y są współdzielone
     * między rankingiem, rekomendacjami, analityką i eksperymentami.
     */
    public LearningToRankService(FeatureStoreService featureStoreService) {
        this.featureStoreService = featureStoreService;
    }

    /**
     * Liczy score pojedynczego posta dla konkretnego użytkownika.
     *
     * Parametry:
     * - userId: użytkownik, dla którego budujemy feed,
     * - post: kandydat do feedu,
     * - source: źródło kandydata, np. FOLLOWING albo RECOMMENDED,
     * - variant: wariant eksperymentu rankingowego, np. ltr_v1 albo ltr_v2.
     *
     * Flow:
     * 1. pobierz feature’y posta,
     * 2. policz freshness,
     * 3. pobierz quality score,
     * 4. pobierz spam penalty,
     * 5. pobierz CTR,
     * 6. dodaj boost zależny od źródła,
     * 7. użyj wag zależnych od wariantu eksperymentu,
     * 8. zwróć RankedCandidate.
     */
    public RankedCandidate score(UUID userId, Post post, String source, String variant) {
        /*
         * Pobieramy feature’y dla jednego posta.
         *
         * Aktualnie metoda pobiera mapę dla listy z jednym ID.
         * To działa, ale przy dużym feedzie lepiej batchować to wyżej:
         *
         * FeedService powinien pobrać feature’y dla wszystkich kandydatów naraz,
         * żeby uniknąć N zapytań do Feature Store.
         */
        Map<UUID, PostFeature> features = featureStoreService.getPostFeatures(
                java.util.List.of(post.getId())
        );

        /*
         * Feature’y posta.
         *
         * Jeśli nie ma jeszcze rekordu w Feature Store,
         * używamy wartości domyślnych.
         */
        PostFeature pf = features.get(post.getId());

        /*
         * Wiek posta w godzinach.
         *
         * Math.max(1.0, ...) zapobiega dzieleniu/ekstremalnym wartościom
         * dla bardzo świeżych postów.
         */
        double ageHours = Math.max(
                1.0,
                Duration.between(post.getCreatedAt(), Instant.now()).toMinutes() / 60.0
        );

        /*
         * Freshness score.
         *
         * Im starszy post, tym mniejsza wartość.
         *
         * Math.exp(-ageHours / 24.0) oznacza wykładniczy spadek świeżości.
         * Po około 24h freshness wyraźnie spada, ale nie znika nagle do zera.
         */
        double freshness = Math.exp(-ageHours / 24.0);

        /*
         * Quality score posta.
         *
         * Może pochodzić z:
         * - jakości treści,
         * - historii autora,
         * - engagementu,
         * - moderacji,
         * - sygnałów użytkowników.
         *
         * Brak feature’ów -> neutralna wartość 0.5.
         */
        double quality = pf == null
                ? 0.5
                : pf.getQualityScore();

        /*
         * Kara za spam.
         *
         * Im większy spamScore, tym bardziej obniżamy ranking posta.
         *
         * Brak feature’ów -> brak kary.
         */
        double spamPenalty = pf == null
                ? 0
                : pf.getSpamScore();

        /*
         * CTR z ostatnich 24h.
         *
         * CTR jest sygnałem, że użytkownicy klikają / otwierają / angażują się
         * w dany post.
         *
         * W tej wersji używamy ctr24h jako prostego sygnału popularności.
         */
        double ctr = pf == null
                ? 0
                : pf.getCtr24h();

        /*
         * Boost zależny od źródła posta.
         *
         * FOLLOWING dostaje najwyższy boost, bo feed powinien respektować
         * świadome decyzje użytkownika, czyli kogo obserwuje.
         *
         * RECOMMENDED dostaje mniejszy boost, bo jest mniej pewnym sygnałem.
         *
         * TRENDING dostaje lekki boost.
         *
         * SPONSORED ma ujemny boost, żeby reklamy nie wygrywały rankingiem
         * wyłącznie przez obecność w systemie. W produkcji sponsored content
         * powinien mieć osobne reguły jakości, aukcji i frequency capping.
         */
        double sourceBoost = switch (source) {
            case "FOLLOWING" -> 0.25;
            case "RECOMMENDED" -> 0.10;
            case "TRENDING" -> 0.05;
            case "SPONSORED" -> -0.05;
            default -> 0;
        };

        /*
         * Właściwy score rankingowy.
         *
         * Variant pochodzi z ExperimentService.
         *
         * ltr_v2 testuje inne wagi:
         * - mniej freshness,
         * - więcej quality,
         * - więcej CTR,
         * - mocniejsza kara za spam.
         *
         * Wariant domyślny działa jak ltr_v1.
         *
         * Dzięki temu można porównać, który ranking daje lepsze metryki:
         * - CTR,
         * - dwell time,
         * - retention,
         * - hide/report rate.
         */
        double score = "ltr_v2".equals(variant)
                ? 0.30 * freshness
                + 0.35 * quality
                + 0.25 * ctr
                + sourceBoost
                - 0.70 * spamPenalty
                : 0.45 * freshness
                + 0.25 * quality
                + 0.15 * ctr
                + sourceBoost
                - 0.60 * spamPenalty;

        /*
         * Zwracamy obiekt rankingowy.
         *
         * reason jest pomocny przy debugowaniu feedu:
         * można zobaczyć, z którego wariantu i źródła pochodził wynik.
         *
         * W produkcji warto rozszerzyć reason/debug info o:
         * - freshness,
         * - quality,
         * - ctr,
         * - spamPenalty,
         * - final score,
         * ale tylko w trybie debug/internal.
         */
        return new RankedCandidate(
                post,
                score,
                "variant=" + variant + ", source=" + source,
                source
        );
    }
}
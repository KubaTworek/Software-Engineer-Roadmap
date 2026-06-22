package com.example.autocomplete.ranking;

import com.example.autocomplete.abtest.ExperimentVariant;
import com.example.autocomplete.model.*;
import com.example.autocomplete.personalization.*;
import com.example.autocomplete.service.TextNormalizer;
import com.example.autocomplete.trending.TrendingStore;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Ranker sugestii autocomplete.
 *
 * Ta klasa odpowiada za finalną kolejność wyników.
 *
 * Indeks zwraca tylko kandydatów pasujących do prefiksu.
 * Ranker decyduje, które z nich są najlepsze.
 *
 * Ranking bierze pod uwagę:
 * - popularność,
 * - CTR,
 * - conversion rate,
 * - freshness,
 * - jakość dopasowania prefixu,
 * - quality score,
 * - personalizację użytkownika,
 * - historię sesji,
 * - locale/country,
 * - trendy,
 * - wariant eksperymentu A/B.
 */
@Component
public class SuggestionRanker {

    /**
     * Ocenia, jak dobrze sugestia pasuje tekstowo do query.
     *
     * Przykład:
     * query = "iph"
     * suggestion = "iPhone 15"
     *
     * Prefix match powinien dostać wysoki score.
     */
    private final PrefixMatchScorer prefixScorer;

    /**
     * Źródło profilu użytkownika.
     *
     * Profil może zawierać:
     * - preferowane kategorie,
     * - preferowane marki,
     * - ostatnie zapytania.
     *
     * Dzięki temu ranking może być inny dla różnych użytkowników.
     */
    private final UserProfileStore profileStore;

    /**
     * Historia aktualnej sesji.
     *
     * Używana do krótkoterminowej personalizacji.
     *
     * Przykład:
     * jeśli użytkownik chwilę temu szukał "macbook",
     * to kolejne sugestie powiązane z Apple mogą dostać boost.
     */
    private final SearchHistoryStore historyStore;

    /**
     * Źródło sygnału trendów.
     *
     * Pozwala podbić sugestie, które są popularne teraz,
     * nawet jeśli nie są historycznie najpopularniejsze.
     */
    private final TrendingStore trendingStore;

    /**
     * Wspólna normalizacja tekstu.
     *
     * Używana przy porównywaniu displayText, marek,
     * historii użytkownika i historii sesji.
     */
    private final TextNormalizer normalizer;

    public SuggestionRanker(
            PrefixMatchScorer prefixScorer,
            UserProfileStore profileStore,
            SearchHistoryStore historyStore,
            TrendingStore trendingStore,
            TextNormalizer normalizer
    ) {
        this.prefixScorer = prefixScorer;
        this.profileStore = profileStore;
        this.historyStore = historyStore;
        this.trendingStore = trendingStore;
        this.normalizer = normalizer;
    }

    /**
     * Rankinguje listę kandydatów i zwraca finalne top N.
     *
     * Flow:
     * 1. Każdy kandydat dostaje score.
     * 2. Odfiltrowujemy słabe lub zablokowane sugestie.
     * 3. Sortujemy malejąco po score.
     * 4. Zwracamy maksymalnie limit wyników.
     *
     * Ta metoda nie pobiera kandydatów z indeksu.
     * Ona tylko ocenia kandydatów dostarczonych przez AutocompleteService.
     */
    public List<RankedSuggestion> rank(
            List<Suggestion> candidates,
            AutocompleteContext ctx,
            int maxPopularity,
            int limit,
            ExperimentVariant variant
    ) {
        return candidates.stream()

                /*
                 * Zamieniamy surową sugestię na RankedSuggestion,
                 * czyli sugestię z policzonym score i dodatkowymi metadanymi.
                 */
                .map(s -> score(s, ctx, maxPopularity, variant))

                /*
                 * Twarde filtrowanie jakościowe.
                 *
                 * Nie pokazujemy sugestii, które:
                 * - mają quality score < 0.30,
                 * - mają finalny score < 0.30,
                 * - są ręcznie zablokowane.
                 *
                 * To jest dodatkowa ochrona po SafetyPolicyFilter.
                 */
                .filter(r ->
                        r.suggestion().metrics().quality() >= .30
                                && r.score() >= .30
                                && !r.suggestion().manuallyBlocked()
                )

                /*
                 * Najlepsze sugestie idą na górę.
                 */
                .sorted(Comparator.comparingDouble(RankedSuggestion::score).reversed())

                /*
                 * Zwracamy tylko tyle sugestii, ile oczekuje klient.
                 */
                .limit(limit)
                .toList();
    }

    /**
     * Liczy finalny score dla jednej sugestii.
     *
     * To jest główna formuła rankingowa.
     *
     * Każdy sygnał jest sprowadzony do zakresu 0.0 - 1.0,
     * a potem ważony.
     */
    private RankedSuggestion score(
            Suggestion s,
            AutocompleteContext ctx,
            int maxPopularity,
            ExperimentVariant variant
    ) {
        /*
         * Normalizacja popularności.
         *
         * Jeśli najpopularniejsza sugestia ma popularity = 1000,
         * a dana sugestia ma popularity = 500,
         * to popularity score = 0.5.
         */
        double popularity = maxPopularity <= 0
                ? 0
                : Math.min(1.0, s.popularity() / (double) maxPopularity);

        /*
         * Jakość dopasowania tekstowego do query.
         *
         * Przykład:
         * query "iph" mocno pasuje do "iPhone 15".
         */
        double prefix = prefixScorer.score(ctx.normalizedQuery(), s);

        /*
         * Personalizacja długoterminowa.
         *
         * Bazuje na profilu użytkownika:
         * - kategorie,
         * - marki,
         * - wcześniejsze zapytania.
         */
        double personalization = personalization(s, ctx);

        /*
         * Dopasowanie sugestii do locale/country.
         *
         * Sugestia dobra dla US niekoniecznie jest dobra dla PL.
         */
        double locale = locale(s, ctx);

        /*
         * Sygnał trendów.
         *
         * Może podbić świeże lub lokalnie popularne sugestie.
         */
        double trending = trendingStore.trendingScore(
                normalizer.normalize(s.displayText()),
                ctx.safeCountry()
        );

        /*
         * Personalizacja krótkoterminowa z aktualnej sesji.
         *
         * Pomaga utrzymać kontekst wpisywania i ostatnich wyszukiwań.
         */
        double session = session(s, ctx);

        /*
         * Boost zależny od wariantu eksperymentu A/B.
         *
         * CTR_HEAVY:
         * - dodatkowo podbija sugestie z wysokim CTR.
         *
         * TRENDING_HEAVY:
         * - dodatkowo podbija sugestie trendujące.
         *
         * CONTROL:
         * - brak dodatkowego boosta.
         */
        double experimentBoost =
                variant == ExperimentVariant.CTR_HEAVY
                        ? s.metrics().ctr() * .10
                        : variant == ExperimentVariant.TRENDING_HEAVY
                        ? trending * .12
                        : 0.0;

        /*
         * Finalna formuła rankingowa.
         *
         * Wagi są dobrane ręcznie na potrzeby projektu.
         * W produkcji byłyby zwykle:
         * - kalibrowane offline,
         * - testowane A/B,
         * - albo zastąpione modelem learning-to-rank.
         */
        double score =
                .15 * popularity
                        + .13 * s.metrics().ctr()
                        + .13 * s.metrics().conversionRate()
                        + .08 * s.metrics().freshness()
                        + .13 * prefix
                        + .09 * s.metrics().quality()
                        + .10 * personalization
                        + .05 * locale
                        + .07 * trending
                        + .04 * session
                        + experimentBoost;

        /*
         * RankedSuggestion zawiera finalny score i najważniejsze sygnały.
         *
         * Te dane są przydatne do debugowania:
         * - dlaczego sugestia jest wysoko,
         * - czy zadziałała personalizacja,
         * - czy zadziałał trend,
         * - czy eksperyment zmienił wynik.
         */
        return new RankedSuggestion(
                s,
                round(score),
                round(personalization),
                round(locale),
                round(trending),
                round(session),
                round(experimentBoost),
                prefix >= .92 ? "prefix" : "token"
        );
    }

    /**
     * Liczy score personalizacji długoterminowej.
     *
     * Źródła:
     * - preferowane kategorie użytkownika,
     * - preferowane marki,
     * - wcześniejsze zapytania z profilu.
     */
    private double personalization(Suggestion s, AutocompleteContext ctx) {
        UserProfile p = profileStore.getProfile(ctx.safeUserId());
        String display = normalizer.normalize(s.displayText());

        double score = 0;

        /*
         * Jeśli sugestia należy do kategorii preferowanej przez użytkownika,
         * dostaje boost.
         *
         * Przykład:
         * użytkownik lubi "electronics",
         * sugestia jest w kategorii "electronics".
         */
        if (s.categories().stream().anyMatch(p.preferredCategories()::contains)) {
            score += .4;
        }

        /*
         * Jeśli tekst sugestii zawiera preferowaną markę,
         * dostaje dodatkowy boost.
         *
         * Przykład:
         * preferowana marka = "apple",
         * sugestia = "Apple Watch" albo "iPhone 15".
         */
        if (p.preferredBrands()
                .stream()
                .anyMatch(b -> display.contains(normalizer.normalize(b)))) {
            score += .35;
        }

        /*
         * Jeśli sugestia jest podobna do wcześniejszych zapytań użytkownika,
         * dostaje kolejny boost.
         *
         * Porównanie działa w obie strony:
         * - display zawiera wcześniejsze query,
         * - albo wcześniejsze query zawiera display.
         */
        if (p.recentQueries()
                .stream()
                .map(normalizer::normalize)
                .anyMatch(q -> display.contains(q) || q.contains(display))) {
            score += .25;
        }

        /*
         * Score personalizacji nie powinien przekroczyć 1.0.
         */
        return Math.min(1.0, score);
    }

    /**
     * Liczy score krótkoterminowego kontekstu sesji.
     *
     * Jeśli użytkownik w tej samej sesji szukał czegoś podobnego,
     * sugestia dostaje pełny session boost.
     */
    private double session(Suggestion s, AutocompleteContext ctx) {
        String d = normalizer.normalize(s.displayText());

        return historyStore.recentSessionQueries(ctx.safeSessionId())
                .stream()
                .map(normalizer::normalize)
                .anyMatch(q -> d.contains(q) || q.contains(d))
                ? 1.0
                : 0.0;
    }

    /**
     * Liczy dopasowanie sugestii do locale i kraju.
     *
     * Wyniki:
     * - 1.0: pasuje locale i country,
     * - 0.5: pasuje tylko jedno z nich,
     * - 0.0: nie pasuje żadne.
     *
     * Puste locales/countries traktujemy jako globalne,
     * czyli pasujące do każdego requestu.
     */
    private double locale(Suggestion s, AutocompleteContext ctx) {
        boolean l = s.locales().isEmpty() || s.locales().contains(ctx.safeLocale());
        boolean c = s.countries().isEmpty() || s.countries().contains(ctx.safeCountry());

        return l && c
                ? 1.0
                : (l || c ? .5 : 0.0);
    }

    /**
     * Zaokrągla score do 4 miejsc po przecinku.
     *
     * Dzięki temu response API i logi są czytelniejsze.
     */
    private double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
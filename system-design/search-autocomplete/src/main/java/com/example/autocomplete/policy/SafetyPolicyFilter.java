package com.example.autocomplete.policy;

import com.example.autocomplete.model.Suggestion;
import com.example.autocomplete.service.TextNormalizer;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Filtr bezpieczeństwa dla sugestii autocomplete.
 *
 * Jego zadanie:
 * - nie dopuścić do pokazania zablokowanych sugestii,
 * - odfiltrować spam,
 * - usunąć sugestie zawierające ryzykowne frazy,
 * - odrzucić sugestie o bardzo niskiej jakości.
 *
 * Ten filtr działa przed rankingiem albo przed finalnym zwróceniem wyników.
 * Dzięki temu ranker nie promuje sugestii, których i tak nie wolno pokazać.
 */
@Component
public class SafetyPolicyFilter {

    /**
     * Normalizer sprowadza tekst sugestii do wspólnej postaci.
     *
     * Dzięki temu blokowanie działa niezależnie od wielkości liter,
     * znaków specjalnych czy nadmiarowych spacji.
     *
     * Przykład:
     * "FREE!!! Free   Free" -> "free free free"
     */
    private final TextNormalizer normalizer;

    /**
     * Prosta lista zablokowanych fraz.
     *
     * W tej wersji jest trzymana w kodzie, ale w produkcji zwykle byłaby
     * pobierana z:
     * - bazy danych,
     * - panelu administracyjnego,
     * - systemu policy/safety,
     * - konfiguracji aktualizowanej bez deploya.
     *
     * Przykłady:
     * - "spam" blokuje ewidentne spamowe sugestie,
     * - "leaked password" blokuje sugestie związane z wyciekiem haseł,
     * - "credit card dump" blokuje frazy finansowo-abuse'owe,
     * - "private ssn" blokuje sugestie związane z prywatnymi danymi.
     */
    private final Set<String> blockedTerms = Set.of(
            "free free free",
            "spam",
            "leaked password",
            "credit card dump",
            "private ssn"
    );

    public SafetyPolicyFilter(TextNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    /**
     * Ocenia pojedynczą sugestię pod kątem bezpieczeństwa.
     *
     * Zwraca PolicyDecision:
     * - allow: sugestia może przejść dalej,
     * - block: sugestia powinna zostać odfiltrowana wraz z powodem blokady.
     *
     * Powód blokady jest ważny do:
     * - debugowania,
     * - logów,
     * - metryk,
     * - audytu jakości danych.
     */
    public PolicyDecision evaluate(Suggestion suggestion) {

        /*
         * Najsilniejsza reguła: ręczna blokada.
         *
         * Jeśli sugestia jest oznaczona jako manuallyBlocked,
         * nie analizujemy jej dalej — od razu blokujemy.
         *
         * To jest przydatne, gdy operator/system safety musi szybko usunąć
         * konkretną sugestię z produkcji.
         */
        if (suggestion.manuallyBlocked()) {
            return PolicyDecision.block("manually_blocked");
        }

        /*
         * Normalizujemy displayText, żeby porównywanie z blockedTerms
         * było stabilne i odporne na różnice w zapisie.
         */
        String text = normalizer.normalize(suggestion.displayText());

        /*
         * Blokujemy sugestię, jeśli zawiera którąkolwiek frazę z blocklisty.
         *
         * Używamy contains(), więc blokada zadziała również wtedy,
         * gdy zablokowana fraza jest częścią dłuższej sugestii.
         *
         * Przykład:
         * displayText = "best credit card dump forum"
         * blockedTerm = "credit card dump"
         */
        for (String term : blockedTerms) {
            if (text.contains(term)) {
                return PolicyDecision.block("blocked_term:" + term);
            }
        }

        /*
         * Blokujemy sugestie o bardzo niskim quality score.
         *
         * quality score może pochodzić np. z:
         * - offline pipeline'u,
         * - heurystyk jakości,
         * - moderacji,
         * - modelu antyspamowego,
         * - oceny danych historycznych.
         *
         * W tym projekcie próg 0.20 oznacza:
         * sugestia jest na tyle słaba, że nie powinna trafić do użytkownika.
         */
        if (suggestion.metrics().quality() < 0.20) {
            return PolicyDecision.block("quality_too_low");
        }

        /*
         * Jeśli żadna reguła blokująca nie zadziałała,
         * sugestia może przejść dalej do rankingu/odpowiedzi.
         */
        return PolicyDecision.allow();
    }
}
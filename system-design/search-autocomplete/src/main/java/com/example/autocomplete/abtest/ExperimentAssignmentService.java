package com.example.autocomplete.abtest;

import org.springframework.stereotype.Component;

/**
 * Serwis przypisujący użytkownika do wariantu eksperymentu A/B.
 *
 * W kontekście autocomplete wariant eksperymentu może wpływać np. na ranking:
 * - CONTROL: standardowy ranking,
 * - CTR_HEAVY: większy wpływ CTR,
 * - TRENDING_HEAVY: większy wpływ trendów.
 *
 * Najważniejsza cecha:
 * ten sam userId dla tego samego experimentName powinien zawsze dostać
 * ten sam wariant eksperymentu.
 *
 * Dzięki temu użytkownik nie widzi losowo zmieniającego się zachowania
 * przy każdym requestcie.
 */
@Component
public class ExperimentAssignmentService {

    /**
     * Przypisuje użytkownika do wariantu eksperymentu.
     *
     * @param userId identyfikator użytkownika; jeśli brak, używamy "anonymous"
     * @param experimentName nazwa eksperymentu, np. "ranking-v6"
     * @return wariant eksperymentu przypisany stabilnie na podstawie userId + experimentName
     */
    public ExperimentVariant assign(String userId, String experimentName) {

        /*
         * Budujemy stabilny klucz eksperymentu.
         *
         * Łączymy userId i experimentName, żeby ten sam użytkownik mógł
         * być przypisany do różnych wariantów w różnych eksperymentach.
         *
         * Przykład:
         * u-123:ranking-v6
         * u-123:new-cache-policy
         *
         * To mogą być dwa niezależne eksperymenty.
         */
        String key = (userId == null ? "anonymous" : userId) + ":" + experimentName;

        /*
         * Zamieniamy klucz na bucket 0-99.
         *
         * hashCode() daje deterministyczną wartość dla tego samego Stringa
         * w obrębie Javy, więc użytkownik trafia stabilnie do tego samego bucketa.
         *
         * Modulo 100 daje prosty procentowy podział ruchu.
         */
        int bucket = Math.abs(key.hashCode()) % 100;

        /*
         * 60% ruchu trafia do wariantu kontrolnego.
         *
         * CONTROL to baseline, z którym porównujemy inne warianty.
         */
        if (bucket < 60) {
            return ExperimentVariant.CONTROL;
        }

        /*
         * 20% ruchu trafia do wariantu mocniej promującego CTR.
         *
         * Ten wariant pozwala sprawdzić, czy większa waga CTR
         * poprawia klikalność i jakość sugestii.
         */
        if (bucket < 80) {
            return ExperimentVariant.CTR_HEAVY;
        }

        /*
         * Pozostałe 20% ruchu trafia do wariantu mocniej promującego trendy.
         *
         * Ten wariant pozwala sprawdzić, czy świeże/trending sugestie
         * poprawiają doświadczenie użytkownika.
         */
        return ExperimentVariant.TRENDING_HEAVY;
    }
}
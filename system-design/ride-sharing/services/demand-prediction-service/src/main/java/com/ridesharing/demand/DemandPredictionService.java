package com.ridesharing.demand;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Serwis prognozowania popytu na przejazdy.
 *
 * W Etapie 4 jest to baseline dla Demand Prediction Service.
 * Nie używa jeszcze prawdziwego modelu ML, ale daje prostą predykcję opartą o:
 * - miasto,
 * - opcjonalną komórkę H3,
 * - ostatnio zarejestrowane requesty,
 * - horyzont prognozy.
 *
 * Wynik może być używany przez:
 * - driver positioning,
 * - dynamic pricing,
 * - dashboard operacyjny,
 * - przyszły ML matching.
 */
@Service
public class DemandPredictionService {

    /**
     * Prosty licznik ostatnich requestów per miasto i per H3 cell.
     *
     * Klucz ma format:
     * - cityId:h3Cell dla konkretnej komórki,
     * - cityId:city dla agregatu miejskiego.
     *
     * ConcurrentHashMap pozwala bezpiecznie aktualizować liczniki z wielu wątków.
     * LongAdder jest lepszy niż AtomicLong przy częstych inkrementacjach,
     * bo lepiej skaluje się przy konkurencyjnym zapisie.
     *
     * Ważne: to jest pamięć lokalna procesu.
     * Po restarcie dane znikną, a przy wielu instancjach każda instancja będzie miała własne liczniki.
     */
    private final Map<String, LongAdder> recentRequests = new ConcurrentHashMap<>();

    /**
     * Zwraca prognozę popytu dla miasta i opcjonalnej komórki H3.
     *
     * Flow:
     * 1. Buduje klucz cityId:h3Cell albo cityId:city.
     * 2. Pobiera liczbę ostatnich requestów z pamięci.
     * 3. Dobiera bazowy popyt dla miasta.
     * 4. Skaluje wynik przez horyzont czasowy.
     * 5. Zwraca forecast z oczekiwaną liczbą requestów i prostym confidence score.
     *
     * To jest deterministyczny baseline.
     * Produkcyjnie w tym miejscu można podpiąć model ML albo feature store.
     */
    public DemandForecastResponse forecast(
            String cityId,
            String h3Cell,
            int horizonMinutes
    ) {
        /*
         * Jeśli h3Cell nie jest podane, prognozujemy na poziomie całego miasta.
         * Jeśli jest podane, prognoza dotyczy konkretnej komórki geograficznej.
         */
        String key = cityId + ":" + (h3Cell == null ? "city" : h3Cell);

        /*
         * Liczba ostatnio zarejestrowanych requestów dla danego obszaru.
         *
         * Uwaga: new LongAdder() w getOrDefault nie zapisuje nic do mapy.
         * To tylko bezpieczny sposób zwrócenia 0, gdy klucz nie istnieje.
         */
        double recent = recentRequests
                .getOrDefault(key, new LongAdder())
                .sum();

        /*
         * Bazowy popyt per miasto.
         *
         * To ręcznie ustawione wartości, które zastępują prawdziwy model predykcyjny.
         * Większe miasta mają większy baseline.
         */
        double base = switch (cityId.toLowerCase()) {
            case "warsaw" -> 18.0;
            case "krakow" -> 11.0;
            case "london" -> 42.0;
            default -> 8.0;
        };

        /*
         * Skaluje prognozę do horyzontu czasowego.
         *
         * 15 minut jest bazowym horyzontem.
         * Math.max(0.25, ...) zapobiega zbyt małemu mnożnikowi dla bardzo krótkich horyzontów.
         */
        double horizonFactor = Math.max(
                0.25,
                horizonMinutes / 15.0
        );

        /*
         * Finalna prognoza.
         *
         * recent * 0.7 oznacza, że świeże requesty zwiększają przewidywany popyt,
         * ale nie 1:1. To proste wygładzenie sygnału.
         */
        double expected = (base + recent * 0.7) * horizonFactor;

        return new DemandForecastResponse(
                cityId,
                h3Cell,
                horizonMinutes,
                round(expected),

                /*
                 * Stały confidence score.
                 * W prawdziwym ML confidence powinien zależeć od jakości danych,
                 * ilości historii, stabilności obszaru i błędu modelu.
                 */
                0.76,

                /*
                 * Feature/debug breakdown.
                 * Przydatne, żeby zrozumieć, skąd wzięła się prognoza.
                 */
                Map.of(
                        "cityBaseline", base,
                        "recentRequests", recent,
                        "horizonFactor", horizonFactor
                ),

                Instant.now()
        );
    }

    /**
     * Rejestruje nowy request przejazdu jako sygnał popytu.
     *
     * Metoda aktualizuje dwa liczniki:
     * - konkretny obszar H3: cityId:h3Cell,
     * - całe miasto: cityId:city.
     *
     * Dzięki temu można prognozować zarówno lokalnie, jak i globalnie dla miasta.
     */
    public void recordRideRequested(String cityId, String h3Cell) {
        /*
         * Licznik dla konkretnej komórki H3.
         *
         * Uwaga: jeśli h3Cell jest null, klucz będzie miał postać cityId:null.
         * Warto to doprecyzować produkcyjnie, żeby nie mieszać null z realną komórką.
         */
        recentRequests
                .computeIfAbsent(cityId + ":" + h3Cell, k -> new LongAdder())
                .increment();

        /*
         * Licznik agregatu miejskiego.
         */
        recentRequests
                .computeIfAbsent(cityId + ":city", k -> new LongAdder())
                .increment();
    }

    /**
     * Zaokrągla wynik do dwóch miejsc po przecinku.
     *
     * Dzięki temu API nie zwraca długich wartości zmiennoprzecinkowych.
     */
    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
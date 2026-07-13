package com.ridesharing.mlmatching;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;

/**
 * Serwis rankingowy ML Matching Service.
 *
 * W aplikacji ride-sharing ten komponent odpowiada za posortowanie kandydatów
 * kierowców dla konkretnego przejazdu.
 *
 * Ważne: ten serwis nie rezerwuje kierowcy i nie zmienia statusu przejazdu.
 * On tylko liczy ranking. Atomowe przypisanie kierowcy nadal musi wykonać
 * core Matching Service / Ride Service.
 *
 * Aktualna implementacja to baseline scoringowy, nie prawdziwy XGBoost/ML,
 * mimo nazwy modelu "matching-xgb-baseline-v4".
 */
@Service
public class MatchingModelService {

    /**
     * Rankinguje kandydatów kierowców dla danego przejazdu.
     *
     * Flow:
     * 1. Przechodzi po kandydatach z requestu.
     * 2. Dla każdego liczy score na podstawie cech.
     * 3. Buduje feature breakdown.
     * 4. Przypisuje krótki reason.
     * 5. Sortuje kandydatów rosnąco po score.
     * 6. Zwraca ranking z wersją modelu i timestampem.
     *
     * Niższy score oznacza lepszego kandydata.
     */
    public MatchingRankResponse rank(MatchingRankRequest request) {
        var ranked = request.candidates()
                .stream()
                .map(c -> {
                    /*
                     * Startowy score.
                     *
                     * Ten model działa jak scoring kosztu:
                     * obniżamy score za dobre cechy,
                     * podbijamy score za złe cechy.
                     *
                     * Potem sortujemy rosnąco, więc najniższy score wygrywa.
                     */
                    double score = 100.0;

                    /*
                     * Im większe prawdopodobieństwo akceptacji, tym lepiej.
                     *
                     * Uwaga: przy acceptanceProbability = 0.9 odejmujemy 31.5 punktu,
                     * więc kandydat, który prawdopodobnie zaakceptuje kurs,
                     * mocno przesuwa się w górę rankingu.
                     */
                    score -= c.acceptanceProbability() * 35.0;

                    /*
                     * Wyższy rating kierowcy obniża score.
                     *
                     * Przy ratingu 5.0 odejmujemy aż 30 punktów.
                     */
                    score -= c.driverRating() * 6.0;

                    /*
                     * Im większe ETA do pickup pointu, tym gorzej.
                     */
                    score += c.pickupEtaMinutes() * 4.0;

                    /*
                     * Dłuższy dystans do pasażera pogarsza ranking.
                     *
                     * To jest prosty proxy dla kosztu dojazdu.
                     */
                    score += c.distanceKm() * 2.5;

                    /*
                     * Ostatnie anulowania kierowcy zwiększają ryzyko,
                     * że kierowca nie dowiezie dobrego doświadczenia.
                     */
                    score += c.recentCancellationCount() * 5.0;

                    /*
                     * Jeśli kierowca jedzie w tym samym kierunku,
                     * delikatnie poprawiamy jego ranking.
                     */
                    score -= c.sameDirection() ? 4.0 : 0.0;

                    /*
                     * Surge multiplier lekko obniża score wszystkim kandydatom.
                     *
                     * To jest globalna cecha requestu, nie cecha konkretnego kierowcy.
                     * Ponieważ odejmujemy ją każdemu kandydatowi tak samo,
                     * nie zmienia kolejności rankingu w ramach jednego requestu.
                     */
                    score -= Math.min(
                            10.0,
                            request.surgeMultiplier() * 2.0
                    );

                    /*
                     * Feature breakdown dla debugowania i audytu rankingu.
                     *
                     * To nie są wkłady punktowe, tylko surowe wartości cech.
                     */
                    var f = new LinkedHashMap<String, Double>();
                    f.put("pickupEtaMinutes", c.pickupEtaMinutes());
                    f.put("distanceKm", c.distanceKm());
                    f.put("driverRating", c.driverRating());
                    f.put("acceptanceProbability", c.acceptanceProbability());
                    f.put(
                            "recentCancellationCount",
                            (double) c.recentCancellationCount()
                    );

                    return new RankedDriver(
                            c.driverId(),
                            round(score),
                            reason(c),
                            f
                    );
                })

                /*
                 * Niższy score oznacza lepszego kandydata.
                 */
                .sorted(Comparator.comparingDouble(RankedDriver::score))
                .toList();

        return new MatchingRankResponse(
                "matching-xgb-baseline-v4",
                request.rideId(),
                ranked,
                Instant.now()
        );
    }

    /**
     * Zwraca uproszczony powód wysokiego miejsca w rankingu.
     *
     * Reason jest przydatny dla debugowania, supportu i obserwowalności.
     * Nie powinien jednak zdradzać zbyt dużo użytkownikowi końcowemu,
     * bo ranking matchingowy ma wartość biznesową i może być podatny na gaming.
     */
    private String reason(MatchingCandidate c) {
        /*
         * Najlepszy sygnał: kandydat ma wysoką szansę akceptacji
         * i krótki czas dojazdu do pasażera.
         */
        if (c.acceptanceProbability() >= 0.85 && c.pickupEtaMinutes() <= 5) {
            return "high_acceptance_low_eta";
        }

        /*
         * Drugi sygnał: bardzo wysoki rating.
         */
        if (c.driverRating() >= 4.8) {
            return "high_rating";
        }

        /*
         * Domyślny powód dla kandydatów bez jednego dominującego sygnału.
         */
        return "balanced_candidate";
    }

    /**
     * Zaokrągla score do dwóch miejsc po przecinku.
     */
    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
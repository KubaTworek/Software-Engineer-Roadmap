package com.ridesharing.mleta;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;

/**
 * Serwis predykcji ETA.
 *
 * W aplikacji ride-sharing ETA jest używane do:
 * - pokazania pasażerowi przewidywanego czasu przejazdu,
 * - sortowania kandydatów w matchingu,
 * - kalkulacji ceny,
 * - wykrywania opóźnień,
 * - porównywania jakości tras i kierowców.
 *
 * Ta implementacja to baseline liniowy, nie prawdziwy model ML.
 * Liczy ETA na podstawie dystansu, bazowej prędkości miasta, ruchu,
 * pogody i drobnej korekty typu pojazdu.
 */
@Service
public class EtaModelService {

    /**
     * Wylicza predykcję ETA dla przekazanego requestu.
     *
     * Flow:
     * 1. Ustala dystans trasy.
     * 2. Dobiera bazową prędkość dla miasta.
     * 3. Ustala mnożnik ruchu.
     * 4. Ustala mnożnik pogody.
     * 5. Dodaje karę/czas pickup zależny od typu pojazdu.
     * 6. Liczy ETA.
     * 7. Liczy prosty confidence score.
     * 8. Zwraca predykcję z wersją modelu i breakdownem cech.
     */
    public EtaPredictionResponse predict(EtaPredictionRequest request) {
        /*
         * Jeśli request zawiera routeDistanceKm, używamy dystansu trasowego.
         *
         * Jeśli nie, liczymy dystans Haversine między origin i destination
         * i mnożymy przez 1.25, żeby przybliżyć dystans drogowy.
         *
         * To jest fallback. Produkcyjnie routeDistanceKm powinno pochodzić
         * z Maps/Routing Service.
         */
        double distance = request.routeDistanceKm() != null
                ? request.routeDistanceKm()
                : haversineKm(
                request.origin(),
                request.destination()
        ) * 1.25;

        /*
         * Bazowa średnia prędkość per miasto.
         *
         * Niższa prędkość oznacza dłuższe ETA.
         * London ma niższą wartość, bo zwykle ruch miejski jest cięższy.
         */
        double baseSpeedKmh = switch (request.cityId().toLowerCase()) {
            case "warsaw" -> 24.0;
            case "krakow" -> 22.0;
            case "london" -> 18.0;
            default -> 21.0;
        };

        /*
         * Traffic multiplier.
         *
         * Jeśli request poda trafficIndex, ograniczamy go do zakresu 0.8–2.5.
         * Jeśli nie poda, bierzemy prostą wartość domyślną zależną od godziny.
         */
        double traffic = request.trafficIndex() != null
                ? clamp(request.trafficIndex(), 0.8, 2.5)
                : defaultTraffic(request.hourOfDay());

        /*
         * Weather multiplier.
         *
         * Pogoda może wydłużyć ETA, ale guardrail 1.0–1.6 chroni przed
         * przesadnie dużym wpływem pojedynczego inputu.
         */
        double weather = request.weatherPenalty() != null
                ? clamp(request.weatherPenalty(), 1.0, 1.6)
                : 1.0;

        /*
         * Korekta pickup/vehicle.
         *
         * Premium ma mniejszą karę, bo zakładamy lepszą dostępność/jakość
         * lub krótszy czas operacyjny. Standard ma większą wartość.
         *
         * To jest heurystyka, nie wytrenowana cecha.
         */
        double pickupPenalty = "premium".equalsIgnoreCase(request.vehicleType())
                ? 0.8
                : 1.2;

        /*
         * Formula ETA:
         *
         * czas bazowy = distance / speed * 60 minut
         * potem mnożniki traffic i weather
         * na końcu stała kara pickup.
         */
        double eta =
                ((distance / baseSpeedKmh) * 60.0 * traffic * weather)
                        + pickupPenalty;

        /*
         * Confidence spada, gdy traffic mocno odbiega od 1.0.
         *
         * Intuicja: im bardziej nietypowy ruch, tym mniej pewna predykcja.
         * Wynik jest ograniczony do zakresu 0.55–0.94.
         */
        double confidence = Math.max(
                0.55,
                Math.min(
                        0.94,
                        0.92 - Math.abs(traffic - 1.0) * 0.08
                )
        );

        /*
         * Breakdown cech użytych w predykcji.
         *
         * Przydatne do debugowania, obserwowalności i audytu modelu.
         */
        var contributions = new LinkedHashMap<String, Double>();
        contributions.put("distanceKm", distance);
        contributions.put("baseSpeedKmh", baseSpeedKmh);
        contributions.put("trafficMultiplier", traffic);
        contributions.put("weatherMultiplier", weather);

        /*
         * Zwracamy:
         * - modelVersion,
         * - ETA,
         * - dolny i górny przedział,
         * - confidence,
         * - contributions,
         * - timestamp predykcji.
         */
        return new EtaPredictionResponse(
                "eta-linear-baseline-v4",
                round(eta),
                round(eta * 0.88),
                round(eta * 1.35),
                round(confidence),
                contributions,
                Instant.now()
        );
    }

    /**
     * Domyślny traffic multiplier na podstawie godziny.
     *
     * Godziny 7–9 i 16–18 są traktowane jako szczyt komunikacyjny.
     * Poza szczytem ruch jest mniejszy, ale nadal lekko zwiększa ETA.
     */
    private double defaultTraffic(Integer hour) {
        if (hour == null) {
            return 1.12;
        }

        return (hour >= 7 && hour <= 9)
                || (hour >= 16 && hour <= 18)
                ? 1.45
                : 1.08;
    }

    /**
     * Liczy dystans Haversine między dwoma punktami.
     *
     * To dystans "po prostej" po powierzchni Ziemi.
     * Dla ETA jest tylko fallbackiem, bo realna trasa drogowa może być znacznie dłuższa.
     */
    private double haversineKm(Coordinate a, Coordinate b) {
        double r = 6371.0;

        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLng = Math.toRadians(b.lng() - a.lng());

        double x =
                Math.sin(dLat / 2) * Math.sin(dLat / 2)
                        + Math.cos(Math.toRadians(a.lat()))
                        * Math.cos(Math.toRadians(b.lat()))
                        * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        return 2 * r * Math.atan2(
                Math.sqrt(x),
                Math.sqrt(1 - x)
        );
    }

    /**
     * Ogranicza wartość do bezpiecznego zakresu.
     *
     * W modelach predykcyjnych guardrails są ważne,
     * bo pojedynczy błędny input nie powinien rozwalić wyniku.
     */
    private double clamp(double v, double min, double max) {
        return Math.max(
                min,
                Math.min(max, v)
        );
    }

    /**
     * Zaokrągla wartość do dwóch miejsc po przecinku.
     */
    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
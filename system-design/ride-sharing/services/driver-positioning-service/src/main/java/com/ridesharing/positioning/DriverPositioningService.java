package com.ridesharing.positioning;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

/**
 * Serwis generujący rekomendacje relokacji kierowcy.
 *
 * W aplikacji ride-sharing driver positioning pomaga kierowcom ustawić się tam,
 * gdzie prawdopodobnie pojawi się większy popyt na przejazdy.
 *
 * Ten serwis korzysta z Demand Prediction Service, żeby oszacować popyt
 * w kilku kandydackich obszarach, a następnie rankinguje je prostym scorem:
 *
 * score = predictedDemand / distancePenalty
 *
 * To jest baseline Etapu 4, nie pełny model ML.
 */
@Service
public class DriverPositioningService {

    /**
     * Klient HTTP do komunikacji z Demand Prediction Service.
     *
     * Serwis pozycjonowania nie prognozuje popytu samodzielnie.
     * Pobiera sygnał demand z osobnego komponentu.
     */
    private final RestClient restClient;

    /**
     * Bazowy URL Demand Prediction Service.
     *
     * Domyślnie wskazuje na lokalny serwis na porcie 8088.
     * W Docker Compose / Kubernetes powinien wskazywać nazwę usługi,
     * np. http://demand-service:8088.
     */
    private final String demandServiceUrl;

    /**
     * Konfiguruje klienta HTTP i URL Demand Service.
     */
    public DriverPositioningService(
            RestClient.Builder builder,
            @Value("${app.demand-service-url:http://localhost:8088}") String demandServiceUrl
    ) {
        this.restClient = builder.build();
        this.demandServiceUrl = demandServiceUrl;
    }

    /**
     * Buduje listę rekomendowanych obszarów dla kierowcy.
     *
     * Flow:
     * 1. Definiuje kandydackie obszary: nearby-a, nearby-b, airport, downtown.
     * 2. Dla każdego obszaru pobiera prognozę popytu z Demand Service.
     * 3. Nakłada prostą karę za dystans.
     * 4. Liczy repositionScore.
     * 5. Sortuje rekomendacje malejąco po score.
     * 6. Zwraca listę rekomendacji z timestampem.
     *
     * W realnym systemie lista kandydatów powinna pochodzić z H3 ringów,
     * hotspotów miasta albo modelu predykcyjnego, a nie ze statycznej listy.
     */
    public DriverPositioningResponse recommend(
            String driverId,
            String cityId,
            double lat,
            double lng
    ) {
        /*
         * Statyczne kandydackie obszary dla MVP.
         *
         * nearby-a / nearby-b symulują pobliskie komórki H3.
         * airport i downtown symulują typowe hotspoty popytu.
         */
        var cells = List.of(
                "nearby-a",
                "nearby-b",
                "airport",
                "downtown"
        );

        var recs = cells.stream()
                .map(cell -> {
                    /*
                     * Prognoza popytu dla konkretnego miasta i obszaru.
                     * Im większy demand, tym atrakcyjniejszy obszar.
                     */
                    double demand = fetchDemand(cityId, cell);

                    /*
                     * Kara za odległość.
                     *
                     * nearby-a ma małą karę, bo jest blisko.
                     * downtown średnią.
                     * airport/nearby-b większą.
                     *
                     * To przybliża koszt przemieszczenia kierowcy.
                     */
                    double distancePenalty = cell.equals("nearby-a")
                            ? 1.0
                            : cell.equals("downtown")
                            ? 1.8
                            : 2.5;

                    /*
                     * Prosty scoring:
                     * wysoki popyt jest dobry, ale daleki obszar obniża opłacalność relokacji.
                     */
                    double score = demand / distancePenalty;

                    return new DriverPositioningRecommendation(
                            cell,

                            /*
                             * Symulowana lokalizacja rekomendowanego obszaru.
                             * Produkcyjnie powinny to być współrzędne centroidu H3 cell
                             * albo realnego hotspotu.
                             */
                            lat + offset(cell),
                            lng + offset(cell) / 2.0,

                            round(demand),
                            round(score),
                            reason(cell)
                    );
                })

                /*
                 * Najlepsze rekomendacje idą na początek listy.
                 */
                .sorted((a, b) -> Double.compare(
                        b.repositionScore(),
                        a.repositionScore()
                ))
                .toList();

        return new DriverPositioningResponse(
                driverId,
                cityId,
                recs,
                Instant.now()
        );
    }

    /**
     * Pobiera prognozę popytu z Demand Prediction Service.
     *
     * Endpoint:
     * GET /api/v1/demand/forecast?cityId=...&h3Cell=...&horizonMinutes=20
     *
     * Obecna implementacja nie parsuje odpowiedzi jako JSON.
     * Zamiast tego używa hashCode odpowiedzi, żeby uzyskać deterministycznie zmienną liczbę.
     *
     * To jest mock/baseline do spięcia architektury.
     * Produkcyjnie trzeba użyć DTO, np. DemandForecastResponse, i odczytać expectedDemand.
     */
    private double fetchDemand(String cityId, String cell) {
        try {
            var response = restClient.get()
                    .uri(
                            demandServiceUrl
                                    + "/api/v1/demand/forecast?cityId={cityId}&h3Cell={cell}&horizonMinutes=20",
                            cityId,
                            cell
                    )
                    .retrieve()
                    .body(String.class);

            /*
             * Fallback, gdy odpowiedź jest pusta.
             */
            return response == null
                    ? 8.0
                    : 10.0 + Math.abs(response.hashCode() % 20);
        } catch (Exception ex) {
            /*
             * Awaryjne wartości popytu, gdy Demand Service nie odpowiada.
             *
             * Dzięki temu positioning nadal działa w trybie baseline,
             * nawet jeśli zależność zewnętrzna jest chwilowo niedostępna.
             */
            return switch (cell) {
                case "downtown" -> 24.0;
                case "airport" -> 18.0;
                default -> 10.0;
            };
        }
    }

    /**
     * Zwraca krótki powód rekomendacji.
     *
     * Frontend może go pokazać kierowcy albo użyć do debugowania,
     * dlaczego dana lokalizacja została zaproponowana.
     */
    private String reason(String cell) {
        return switch (cell) {
            case "airport" -> "airport_arrivals_forecast";
            case "downtown" -> "high_city_center_demand";
            default -> "nearby_balanced_supply_demand";
        };
    }

    /**
     * Generuje małe przesunięcie współrzędnych dla symulowanych obszarów.
     *
     * W MVP pozwala zwrócić różne punkty rekomendacji na mapie.
     * Produkcyjnie należy używać prawdziwych centroidów H3 albo geometrii hotspotów.
     */
    private double offset(String cell) {
        return Math.abs(cell.hashCode() % 100) / 10000.0;
    }

    /**
     * Zaokrągla wartość do dwóch miejsc po przecinku.
     */
    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
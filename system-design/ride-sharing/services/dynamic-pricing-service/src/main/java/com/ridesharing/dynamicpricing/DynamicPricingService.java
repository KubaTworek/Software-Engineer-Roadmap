package com.ridesharing.dynamicpricing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;

/**
 * Serwis dynamicznej wyceny przejazdu.
 *
 * W Etapie 4 odpowiada za wyliczenie ceny zależnej od aktualnego popytu,
 * podaży kierowców i prognozy popytu z Demand Prediction Service.
 *
 * To jest baseline dynamic pricingu:
 * - liczy bazową cenę,
 * - pobiera forecast popytu,
 * - wylicza liveRatio i forecastRatio,
 * - wyznacza surge multiplier,
 * - nakłada limit maksymalnego surge,
 * - liczy cenę finalną i przewidywany zarobek kierowcy.
 *
 * Produkcyjnie ten serwis powinien mieć silne guardrails,
 * wersjonowanie modeli/taryf i pełny audyt decyzji cenowych.
 */
@Service
public class DynamicPricingService {

    /**
     * Klient HTTP do komunikacji z Demand Prediction Service.
     *
     * DynamicPricingService nie prognozuje popytu samodzielnie.
     * Pobiera sygnał forecastu z osobnego serwisu.
     */
    private final RestClient restClient;

    /**
     * Bazowy URL Demand Prediction Service.
     *
     * Domyślnie:
     * http://localhost:8088
     *
     * W Docker Compose albo Kubernetes powinien wskazywać nazwę usługi,
     * np. http://demand-service:8088.
     */
    private final String demandUrl;

    /**
     * Konfiguruje klienta HTTP i adres Demand Service.
     */
    public DynamicPricingService(
            RestClient.Builder builder,
            @Value("${app.demand-service-url:http://localhost:8088}") String demandUrl
    ) {
        this.restClient = builder.build();
        this.demandUrl = demandUrl;
    }

    /**
     * Wylicza dynamiczną cenę przejazdu.
     *
     * Flow:
     * 1. Pobiera prognozę popytu dla cityId + h3Cell.
     * 2. Zabezpiecza supply przed dzieleniem przez zero.
     * 3. Liczy liveRatio: aktualne requesty / dostępni kierowcy.
     * 4. Liczy forecastRatio: prognozowany popyt / dostępni kierowcy.
     * 5. Liczy rawSurge na podstawie liveRatio i forecastRatio.
     * 6. Ogranicza surge do zakresu 1.00–2.25.
     * 7. Bierze basePrice z requestu albo liczy bazę lokalnie.
     * 8. Mnoży bazę przez surge.
     * 9. Liczy driverEarnings jako 78% finalnej ceny.
     * 10. Zwraca response z breakdownem i informacją o guardrails.
     */
    public DynamicPriceResponse price(DynamicPriceRequest request) {
        /*
         * Forecast popytu z Demand Prediction Service.
         * To sygnał wyprzedzający — cena może rosnąć nie tylko wtedy,
         * gdy już jest duży popyt, ale też gdy system przewiduje jego wzrost.
         */
        double demandForecast = forecastDemand(
                request.cityId(),
                request.h3Cell()
        );

        /*
         * Supply to liczba dostępnych kierowców.
         * Math.max(1.0, ...) chroni przed dzieleniem przez zero.
         *
         * Uwaga: jeśli realnie nie ma kierowców, samo wymuszenie 1.0 ukrywa problem.
         * Produkcyjnie brak podaży powinien być osobnym sygnałem, nie tylko korektą matematyczną.
         */
        double supply = Math.max(1.0, request.availableDrivers());

        /*
         * liveRatio opisuje aktualną relację popytu do podaży.
         *
         * Przykład:
         * activeRequests = 20, availableDrivers = 10 -> liveRatio = 2.0.
         */
        double liveRatio = request.activeRequests() / supply;

        /*
         * forecastRatio opisuje przewidywany popyt względem dostępnych kierowców.
         *
         * To pozwala reagować wcześniej, zanim system faktycznie zostanie przeciążony.
         */
        double forecastRatio = demandForecast / supply;

        /*
         * Surowy surge multiplier.
         *
         * Składniki:
         * - liveRatio powyżej 0.8 zwiększa cenę mocniej,
         * - forecastRatio powyżej 1.0 zwiększa cenę słabiej.
         *
         * Wagi 0.28 i 0.12 są heurystyczne.
         * Nie pochodzą z modelu ML ani kalibracji biznesowej.
         */
        double rawSurge =
                1.0
                        + Math.max(0.0, liveRatio - 0.8) * 0.28
                        + Math.max(0.0, forecastRatio - 1.0) * 0.12;

        /*
         * Guardrails dla surge.
         *
         * Cena nie spada poniżej 1.00x i nie rośnie powyżej 2.25x.
         * To ważne biznesowo i regulacyjnie.
         */
        double cappedSurge = Math.max(
                1.0,
                Math.min(rawSurge, 2.25)
        );

        /*
         * Cena bazowa może przyjść z requestu.
         * Jeśli jej nie ma, liczymy ją lokalnie z dystansu i czasu.
         *
         * Produkcyjnie lepiej, żeby baza pochodziła z taryfy wersjonowanej per city/vehicleType.
         */
        BigDecimal base = request.basePrice() != null
                ? request.basePrice()
                : calculateBase(request);

        /*
         * Surge jako BigDecimal zaokrąglony do dwóch miejsc.
         * To jest wartość pokazywana i używana do mnożenia ceny.
         */
        BigDecimal surge = BigDecimal.valueOf(cappedSurge)
                .setScale(2, RoundingMode.HALF_UP);

        /*
         * Finalna cena po dynamicznym mnożniku.
         */
        BigDecimal finalPrice = base
                .multiply(surge)
                .setScale(2, RoundingMode.HALF_UP);

        /*
         * Szacowany zarobek kierowcy.
         *
         * 0.78 oznacza, że kierowca dostaje 78% ceny,
         * a platforma zatrzymuje 22%.
         */
        BigDecimal driverEarnings = finalPrice
                .multiply(BigDecimal.valueOf(0.78))
                .setScale(2, RoundingMode.HALF_UP);

        /*
         * Breakdown decyzji cenowej.
         * To jest ważne dla debugowania dynamic pricingu i audytu.
         */
        return new DynamicPriceResponse(
                request.cityId(),
                request.h3Cell(),
                finalPrice,
                surge,
                driverEarnings,
                Map.of(
                        "demandForecast", demandForecast,
                        "liveRatio", liveRatio,
                        "forecastRatio", forecastRatio
                ),
                cappedSurge >= 2.25
                        ? "surge_cap_applied"
                        : "within_guardrails",
                Instant.now()
        );
    }

    /**
     * Liczy bazową cenę, gdy request nie zawiera basePrice.
     *
     * Formula:
     * 8.0 + distanceKm * 2.4 + durationMinutes * 0.55
     *
     * To jest uproszczona taryfa domyślna.
     * Produkcyjnie stawki powinny zależeć od miasta, typu pojazdu i wersji taryfy.
     */
    private BigDecimal calculateBase(DynamicPriceRequest r) {
        return BigDecimal
                .valueOf(
                        8.0
                                + r.distanceKm() * 2.4
                                + r.durationMinutes() * 0.55
                )
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Pobiera prognozę popytu z Demand Prediction Service.
     *
     * Endpoint:
     * GET /api/v1/demand/forecast?cityId=...&h3Cell=...&horizonMinutes=15
     *
     * Obecnie odpowiedź nie jest parsowana jako JSON.
     * Zamiast tego używany jest hashCode odpowiedzi, żeby uzyskać pseudozmienną wartość.
     *
     * To jest rozwiązanie demonstracyjne.
     * Produkcyjnie należy sparsować DemandForecastResponse i odczytać expectedDemand.
     */
    private double forecastDemand(String cityId, String h3Cell) {
        try {
            String body = restClient.get()
                    .uri(
                            demandUrl
                                    + "/api/v1/demand/forecast?cityId={cityId}&h3Cell={cell}&horizonMinutes=15",
                            cityId,
                            h3Cell
                    )
                    .retrieve()
                    .body(String.class);

            /*
             * Fallback przy pustej odpowiedzi.
             */
            return body == null
                    ? 8.0
                    : 8.0 + Math.abs(body.hashCode() % 24);
        } catch (Exception ex) {
            /*
             * Fallback przy błędzie Demand Service.
             *
             * Dzięki temu dynamic pricing nie blokuje całego flow,
             * ale cena może być mniej dokładna.
             */
            return 10.0;
        }
    }
}
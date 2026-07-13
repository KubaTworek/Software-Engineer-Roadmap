package com.ridesharing.pricing;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;

/**
 * Serwis odpowiedzialny za wycenę przejazdu w osobnym Pricing Service.
 *
 * W architekturze ride-sharing ten komponent liczy estymowaną cenę dla pasażera
 * oraz przewidywany zarobek kierowcy.
 *
 * Uwzględnia:
 * - taryfę per miasto,
 * - typ pojazdu,
 * - dystans,
 * - czas przejazdu,
 * - prosty surge pricing,
 * - prowizję platformy,
 * - walutę,
 * - czas ważności wyceny.
 *
 * To nadal jest pricing baseline, ale już lepszy niż pojedynczy lokalny PricingService w core-api.
 */
@Service
public class PricingService {

    /**
     * Statyczna mapa taryf.
     *
     * Klucz ma format:
     * cityId:vehicleType
     *
     * Przykład:
     * - warsaw:standard,
     * - krakow:standard,
     * - default:standard.
     *
     * Produkcyjnie taryfy powinny pochodzić z bazy danych, konfiguracji albo pricing control panelu,
     * a nie być zahardkodowane w kodzie.
     */
    private final Map<String, CityTariff> tariffs = Map.of(
            "warsaw:standard",
            new CityTariff(
                    "warsaw",
                    "standard",
                    bd("8.00"),
                    bd("2.40"),
                    bd("0.55"),
                    bd("0.22"),
                    "PLN"
            ),

            "krakow:standard",
            new CityTariff(
                    "krakow",
                    "standard",
                    bd("7.00"),
                    bd("2.25"),
                    bd("0.50"),
                    bd("0.22"),
                    "PLN"
            ),

            "default:standard",
            new CityTariff(
                    "default",
                    "standard",
                    bd("8.00"),
                    bd("2.30"),
                    bd("0.50"),
                    bd("0.22"),
                    "PLN"
            )
    );

    /**
     * Liczy estymowaną cenę przejazdu.
     *
     * Flow:
     * 1. Dobiera taryfę po cityId + vehicleType.
     * 2. Jeżeli nie ma konkretnej taryfy, używa default:standard.
     * 3. Liczy mnożnik surge na podstawie relacji popytu do podaży.
     * 4. Liczy cenę:
     *    baseFare + perKm * distanceKm + perMinute * durationMinutes
     * 5. Mnoży cenę przez surge.
     * 6. Liczy przewidywany zarobek kierowcy po odjęciu prowizji platformy.
     * 7. Zwraca odpowiedź z ceną, zarobkiem kierowcy, walutą i czasem wygaśnięcia.
     *
     * Wynik jest estimate, nie finalnym rozliczeniem.
     * Finalna cena po kursie może zostać przeliczona na podstawie realnego dystansu i czasu.
     */
    public PriceEstimateResponse estimate(PriceEstimateRequest request) {
        /*
         * Dobór taryfy dla miasta i typu pojazdu.
         *
         * Jeśli np. request przyjdzie dla warsaw:standard, użyjemy taryfy warszawskiej.
         * Jeśli nie znajdziemy dopasowania, spadamy do default:standard.
         */
        CityTariff tariff = tariffs.getOrDefault(
                request.cityId() + ":" + request.vehicleType(),
                tariffs.get("default:standard")
        );

        /*
         * Surge zależy od stosunku aktywnych requestów do dostępnych kierowców.
         *
         * Im więcej requestów na jednego kierowcę, tym wyższy mnożnik.
         */
        BigDecimal surge = surge(
                request.activeRequests(),
                request.availableDrivers()
        );

        /*
         * Cena pasażera:
         * opłata bazowa + koszt dystansu + koszt czasu, a potem surge.
         *
         * BigDecimal jest właściwy dla pieniędzy, bo unika typowych błędów double.
         */
        BigDecimal price = tariff.baseFare()
                .add(tariff.perKm().multiply(BigDecimal.valueOf(request.distanceKm())))
                .add(tariff.perMinute().multiply(BigDecimal.valueOf(request.durationMinutes())))
                .multiply(surge)
                .setScale(2, RoundingMode.HALF_UP);

        /*
         * Szacowany zarobek kierowcy po prowizji platformy.
         *
         * platformFeePercent = 0.22 oznacza 22% prowizji platformy,
         * więc kierowca dostaje 78% ceny.
         */
        BigDecimal driverEarnings = price
                .multiply(BigDecimal.ONE.subtract(tariff.platformFeePercent()))
                .setScale(2, RoundingMode.HALF_UP);

        /*
         * Wycena jest ważna 5 minut.
         *
         * To ważne przy dynamic pricingu — pasażer nie powinien zamawiać przejazdu
         * na podstawie starej ceny, gdy popyt/podaż już się zmieniły.
         */
        return new PriceEstimateResponse(
                request.cityId(),
                request.vehicleType(),
                price,
                driverEarnings,
                surge,
                tariff.currency(),
                Instant.now().plusSeconds(300)
        );
    }

    /**
     * Liczy prosty surge multiplier.
     *
     * activeRequests / availableDrivers tworzy wskaźnik popytu do podaży.
     *
     * Reguły:
     * - brak kierowców: 2.00,
     * - ratio < 1.0: 1.00,
     * - ratio < 1.5: 1.20,
     * - ratio < 2.0: 1.50,
     * - ratio >= 2.0: 2.00.
     *
     * To jest uproszczony model. Produkcyjnie surge powinien mieć guardrails:
     * maksymalne limity, wygładzanie, minimalny czas trwania i reguły regulacyjne.
     */
    private BigDecimal surge(int activeRequests, int availableDrivers) {
        /*
         * Jeśli nie ma dostępnych kierowców, cena rośnie do maksymalnego prostego mnożnika.
         * Alternatywnie system mógłby w ogóle nie pokazywać dostępności przejazdu.
         */
        if (availableDrivers <= 0) {
            return bd("2.00");
        }

        double ratio = activeRequests / (double) availableDrivers;

        if (ratio < 1.0) {
            return bd("1.00");
        }

        if (ratio < 1.5) {
            return bd("1.20");
        }

        if (ratio < 2.0) {
            return bd("1.50");
        }

        return bd("2.00");
    }

    /**
     * Tworzy BigDecimal z tekstu.
     *
     * Dla pieniędzy lepiej używać new BigDecimal("8.00") niż BigDecimal.valueOf(8.00),
     * bo string daje pełną kontrolę nad wartością dziesiętną.
     */
    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
package com.ridesharing.mvp.ride;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Prosty serwis pricingu dla MVP.
 *
 * W aplikacji ride-sharing ta klasa odpowiada za orientacyjne wyliczenie ceny przejazdu
 * na podstawie dystansu i czasu trwania trasy.
 *
 * To jest lokalny, uproszczony PricingService w core-api.
 * W późniejszym etapie może zostać zastąpiony osobnym Pricing Service,
 * który uwzględnia surge, miasto, typ pojazdu, promocje i reguły biznesowe.
 */
@Service
public class PricingService {

    /**
     * Liczy szacowaną cenę przejazdu.
     *
     * Aktualny model ceny:
     * cena = opłata bazowa + koszt dystansu + koszt czasu
     *
     * Składniki:
     * - base: stała opłata startowa 8.00,
     * - distance: 2.40 za każdy kilometr,
     * - time: 0.75 za każdą minutę,
     * - wynik zaokrąglony do 2 miejsc po przecinku.
     *
     * Metoda zwraca BigDecimal, bo pieniądze nie powinny być liczone na double
     * ze względu na błędy precyzji liczb zmiennoprzecinkowych.
     */
    public BigDecimal estimatePrice(double distanceKm, int durationMinutes) {
        /*
         * Stała opłata startowa.
         * W realnym systemie byłaby zależna od miasta, typu pojazdu i taryfy.
         */
        var base = BigDecimal.valueOf(8.00);

        /*
         * Koszt dystansu.
         * distanceKm pochodzi zwykle z MapsClient / Routing Service.
         */
        var distance = BigDecimal.valueOf(distanceKm)
                .multiply(BigDecimal.valueOf(2.40));

        /*
         * Koszt czasu.
         * durationMinutes pochodzi z estymacji trasy.
         */
        var time = BigDecimal.valueOf(durationMinutes)
                .multiply(BigDecimal.valueOf(0.75));

        /*
         * Finalna cena orientacyjna.
         * Zaokrąglenie HALF_UP jest typowe dla kwot pokazywanych użytkownikowi.
         */
        return base
                .add(distance)
                .add(time)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
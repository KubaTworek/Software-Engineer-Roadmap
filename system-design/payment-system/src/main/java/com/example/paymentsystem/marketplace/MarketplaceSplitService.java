package com.example.paymentsystem.marketplace;

import org.springframework.stereotype.Service;

/**
 * Serwis odpowiedzialny za podział kwoty płatności w modelu marketplace.
 *
 * W marketplace jedna płatność klienta jest dzielona na:
 * - prowizję platformy,
 * - kwotę należną merchantowi/sprzedawcy.
 *
 * Przykład:
 * Klient płaci 10000, czyli 100,00 PLN.
 *
 * System dzieli to na:
 * - 1000 dla platformy,
 * - 9000 dla merchanta.
 *
 * Ten wynik jest później używany przy księgowaniu w ledgerze.
 */
@Service
public class MarketplaceSplitService {

    /**
     * Dzieli kwotę płatności na platform fee i merchant amount.
     *
     * Obecna reguła jest prosta:
     * - platforma bierze 10%,
     * - merchant dostaje pozostałe 90%.
     *
     * Kwota jest typu long, bo pieniądze przechowujemy w najmniejszej
     * jednostce waluty, np. groszach albo centach.
     *
     * Przykład:
     * amount = 10000
     * platformFee = 1000
     * merchantAmount = 9000
     *
     * Math.max(1, ...) zabezpiecza przed sytuacją, w której dla bardzo małej
     * płatności prowizja wyszłaby jako 0.
     *
     * @param amount pełna kwota płatności
     * @return wynik splitu: prowizja platformy oraz kwota merchanta
     */
    public SplitResult split(long amount) {
        long platformFee = Math.max(1, Math.round(amount * 0.10));

        return new SplitResult(
                platformFee,
                amount - platformFee
        );
    }
}
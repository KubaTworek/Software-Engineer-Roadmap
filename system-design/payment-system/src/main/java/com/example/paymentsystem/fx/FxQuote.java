package com.example.paymentsystem.fx;

import java.time.Instant;

/**
 * Niezmienna decyzja cenowa. Sam kurs nie wystarcza do audytu: trzeba znać
 * źródło, czas ważności i politykę zaokrąglenia obowiązującą w chwili płatności.
 */
public record FxQuote(
        String quoteId,
        String provider,
        Instant quotedAt,
        Instant expiresAt,
        long sourceAmount,
        String sourceCurrency,
        long targetAmount,
        String targetCurrency,
        String rate,
        String roundingPolicy
) {
    public boolean isExpiredAt(Instant instant) { return !instant.isBefore(expiresAt); }
}

package pl.jakubtworek.lld.promotion.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Przedział półotwarty na osi Instant: start włącznie, koniec wyłącznie.
 */
public record CampaignPeriod(Instant from, Instant to) {

    public CampaignPeriod {
        Objects.requireNonNull(from);
        Objects.requireNonNull(to);
        if (!from.isBefore(to))
            throw new IllegalArgumentException("Koniec kampanii musi być po początku");
    }

    public boolean contains(Instant instant) {
        return !instant.isBefore(from) && instant.isBefore(to);
    }
}

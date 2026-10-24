package pl.jakubtworek.lld.promotion.domain;

/**
 * Stabilna, jawna tożsamość konfiguracji i drugi klucz kolejności przy remisie priorytetu.
 */
public record PromotionId(String value) implements Comparable<PromotionId> {

    public PromotionId {
        if (value == null || !value.matches("[A-Z][A-Z0-9_-]{0,39}"))
            throw new IllegalArgumentException("Niepoprawne ID promocji");
    }

    @Override
    public int compareTo(PromotionId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}

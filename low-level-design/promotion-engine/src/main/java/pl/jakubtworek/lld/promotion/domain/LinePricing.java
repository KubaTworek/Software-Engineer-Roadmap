package pl.jakubtworek.lld.promotion.domain;

import java.util.Objects;

/**
 * Bilans jednej linii całego koszyka, po wszystkich zatwierdzonych alokacjach.
 */
public record LinePricing(String sku, Money original, Money discount, Money payable) {

    public LinePricing {
        Objects.requireNonNull(sku);
        Objects.requireNonNull(original);
        Objects.requireNonNull(discount);
        Objects.requireNonNull(payable);
        if (!original.minus(discount).equals(payable))
            throw new IllegalArgumentException("Niespójny bilans linii");
    }
}

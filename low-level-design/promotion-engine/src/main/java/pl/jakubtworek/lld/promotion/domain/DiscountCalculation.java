package pl.jakubtworek.lld.promotion.domain;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Przeprowadzona kalkulacja, także z wynikiem zero. allowed oznacza dopuszczalną
 * kwotę; dopiero decyzja APPLIED zatwierdza jej alokację. BEST_SINGLE przechowuje
 * również kalkulacje przegranych jako alternatywy, bez odjęcia ich od koszyka.
 */
public record DiscountCalculation(Money base, Money budget, Money requested, Money allowed, Map<String,
        Money> allocation) {

    public enum ClippingReason {

        ELIGIBLE_BALANCE, DISCOUNT_LIMIT
    }

    public DiscountCalculation {
        Objects.requireNonNull(base);
        Objects.requireNonNull(budget);
        Objects.requireNonNull(requested);
        Objects.requireNonNull(allowed);
        allocation = Map.copyOf(allocation);
        if (!allowed.equals(requested.min(base).min(budget)))
            throw new IllegalArgumentException("Niespójne przycięcie rabatu");
        Money sum = Money.ZERO;
        for (Money value : allocation.values()) {
            if (value.cents() == 0)
                throw new IllegalArgumentException("Alokacja zapisuje tylko dodatnie udziały");
            sum = sum.plus(value);
        }
        if (!sum.equals(allowed))
            throw new IllegalArgumentException("Alokacja nie zachowuje kwoty rabatu");
    }

    /**
     * Każdy powód oznacza przekroczenie danego ograniczenia przez kwotę nominalną.
     */
    public Set<ClippingReason> clippingReasons() {
        var reasons = new HashSet<ClippingReason>();
        if (requested.compareTo(base) > 0)
            reasons.add(ClippingReason.ELIGIBLE_BALANCE);
        if (requested.compareTo(budget) > 0)
            reasons.add(ClippingReason.DISCOUNT_LIMIT);
        return Set.copyOf(reasons);
    }
}

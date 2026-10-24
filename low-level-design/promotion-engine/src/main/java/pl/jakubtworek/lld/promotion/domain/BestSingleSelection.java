package pl.jakubtworek.lld.promotion.domain;

import java.util.List;
import java.util.Set;
import java.util.function.ToLongFunction;

/**
 * Najkorzystniejsza JEDNA promocja, nie optymalizator kombinacji. Remis kwoty
 * pozostawia pierwszy priorytet/ID, a wynik zero oznacza brak wybranej promocji.
 */
public final class BestSingleSelection implements SelectionPolicy {

    @Override
    public Set<PromotionId> select(List<PromotionId> ordered, ToLongFunction<PromotionId> standaloneBenefit) {
        PromotionId best = null;
        long largest = 0;
        for (PromotionId id : ordered) {
            long benefit = standaloneBenefit.applyAsLong(id);
            if (benefit > largest) {
                largest = benefit;
                best = id;
            }
        }
        return best == null ? Set.of() : Set.of(best);
    }
}

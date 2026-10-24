package pl.jakubtworek.lld.promotion.domain;

import java.util.List;
import java.util.Set;
import java.util.function.ToLongFunction;

/**
 * Dopuszcza wszystkie kandydatury; zgodny zestaw powstaje zachłannie w silniku.
 */
public final class PrioritySelection implements SelectionPolicy {

    @Override
    public Set<PromotionId> select(List<PromotionId> ordered, ToLongFunction<PromotionId> standaloneBenefit) {
        return Set.copyOf(ordered);
    }
}

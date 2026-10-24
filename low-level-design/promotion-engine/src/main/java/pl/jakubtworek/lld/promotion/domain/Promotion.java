package pl.jakubtworek.lld.promotion.domain;

import java.util.Objects;
import java.util.Set;
import java.util.Optional;

/**
 * Kompozycja tożsamości, kwalifikacji, strategii rabatu i zgodności z innymi.
 * Większy priorytet wygrywa kolejność, niekoniecznie cenę. Lista wykluczeń jest
 * kopiowana; implementacje Condition/Discount dostarczone przez klienta muszą
 * przestrzegać ich kontraktu czystości — final referencji tego nie zapewnia.
 */
public record Promotion(PromotionId id, int priority, Condition condition, Discount discount, StackingPolicy stacking,
        Set<PromotionId> excludedIds, DiscountScope scope, Optional<CampaignPeriod> period) {

    public Promotion {
        Objects.requireNonNull(id);
        Objects.requireNonNull(condition);
        Objects.requireNonNull(discount);
        Objects.requireNonNull(stacking);
        excludedIds = Set.copyOf(excludedIds);
        Objects.requireNonNull(scope);
        Objects.requireNonNull(period);
        if (priority < 0)
            throw new IllegalArgumentException("Priorytet nie może być ujemny");
        if (excludedIds.contains(id))
            throw new IllegalArgumentException("Promocja nie może wykluczać siebie");
    }

    /**
     * Dotychczasowy wariant oznacza cały koszyk i kampanię bez ograniczenia dat.
     */
    public Promotion(PromotionId id, int priority, Condition condition, Discount discount, StackingPolicy stacking,
            Set<PromotionId> excludedIds) {
        this(id, priority, condition, discount, stacking, excludedIds, DiscountScope.all(), Optional.empty());
    }

    public Promotion scopedTo(DiscountScope scope) {
        return new Promotion(id, priority, condition, discount, stacking, excludedIds, scope, period);
    }

    public Promotion during(CampaignPeriod period) {
        return new Promotion(id, priority, condition, discount, stacking, excludedIds, scope, Optional.of(period));
    }
}

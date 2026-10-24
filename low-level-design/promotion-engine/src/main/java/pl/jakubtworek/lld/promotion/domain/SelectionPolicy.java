package pl.jakubtworek.lld.promotion.domain;

import java.util.List;
import java.util.Set;
import java.util.function.ToLongFunction;

/**
 * Strategia wybiera ID spośród aktywnych i zakwalifikowanych promocji.
 * Lista ma stabilną kolejność priorytet/ID. Callback udostępnia korzyść jednej
 * promocji od oryginalnego koszyka PO limitach, z cache wewnątrz danej wyceny.
 * Wybór nie nalicza rabatów; wspólna ścieżka silnika nadal chroni zgodność i salda.
 */
@FunctionalInterface
public interface SelectionPolicy {

    Set<PromotionId> select(List<PromotionId> ordered, ToLongFunction<PromotionId> standaloneBenefit);
}

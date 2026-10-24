package pl.jakubtworek.lld.splitwise;

import java.util.Map;
import java.util.Objects;

/**
 * Jawne kwoty nie wymagają zaokrąglania. Konfiguracja kopiuje udziały i liczy
 * sumę raz; split sprawdza zgodność z kwotą konkretnego wydatku. Nie dopisujemy
 * brakującego grosza wybranej osobie, bo zmieniłoby to intencję użytkownika.
 */
public final class ExactSplit implements SplitPolicy {

    private final Map<MemberId, Money> shares;
    private final Money sum;

    public ExactSplit(Map<MemberId, Money> shares) {
        this.shares = Map.copyOf(shares);
        if (this.shares.isEmpty()) {
            throw new IllegalArgumentException("Podział wymaga uczestników");
        }
        Money accumulated = new Money(0);
        for (Money share : this.shares.values()) {
            accumulated = accumulated.plus(share);
        }
        sum = accumulated;
    }

    @Override
    public Map<MemberId, Money> split(Money total) {
        if (!sum.equals(Objects.requireNonNull(total, "total"))) {
            throw new IllegalArgumentException("Suma jawnych kwot różni się od wydatku");
        }
        return shares;
    }
}

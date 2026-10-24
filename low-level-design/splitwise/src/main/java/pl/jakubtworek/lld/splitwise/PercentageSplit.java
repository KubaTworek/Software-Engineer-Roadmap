package pl.jakubtworek.lld.splitwise;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Procenty zapisujemy jako punkty bazowe: 1250=12.50%, suma=10000.
 * Najpierw przydzielamy podłogi dokładnych udziałów, potem po groszu według
 * największej reszty. Remis rozstrzyga ID, nie kolejność iteracji mapy.
 * BigInteger chroni iloczyn total*punkty: finalny udział może mieścić się w long,
 * choć samo mnożenie przepełniłoby long. Nie tracimy precyzji przez double.
 */
public final class PercentageSplit implements SplitPolicy {

    public static final int TOTAL_POINTS = 10_000;
    private final Map<MemberId, Integer> points;

    private record Fraction(MemberId member, int remainder) {
    }

    public PercentageSplit(Map<MemberId, Integer> points) {
        this.points = Map.copyOf(points);
        if (this.points.isEmpty()) {
            throw new IllegalArgumentException("Podział wymaga uczestników");
        }
        long sum = 0;
        for (int value : this.points.values()) {
            if (value <= 0 || value > TOTAL_POINTS) {
                throw new IllegalArgumentException("Udział wymaga 1..10000 punktów");
            }
            sum += value;
        }
        if (sum != TOTAL_POINTS) {
            throw new IllegalArgumentException("Suma procentów musi wynosić 10000 punktów");
        }
    }

    @Override
    public Map<MemberId, Money> split(Money total) {
        Objects.requireNonNull(total, "total");
        var result = new HashMap<MemberId, Money>();
        var fractions = new ArrayList<Fraction>();
        long assigned = 0;
        for (var entry : points.entrySet()) {
            var product = BigInteger.valueOf(total.grosz()).multiply(BigInteger.valueOf(entry.getValue()));
            var division = product.divideAndRemainder(BigInteger.valueOf(TOTAL_POINTS));
            long floor = division[0].longValueExact();
            result.put(entry.getKey(), new Money(floor));
            assigned = Math.addExact(assigned, floor);
            fractions.add(new Fraction(entry.getKey(), division[1].intValueExact()));
        }
        fractions.sort(Comparator.comparingInt(Fraction::remainder).reversed()
                .thenComparing(fraction -> fraction.member().value()));
        long remainder = total.grosz() - assigned;
        // Ponieważ odrzuciliśmy mniej niż grosz na osobę, remainder < liczba
        // osób. Każda z pierwszych remainder dostaje dokładnie jeden grosz.
        for (int index = 0; index < remainder; index++) {
            MemberId member = fractions.get(index).member();
            result.put(member, result.get(member).plus(new Money(1)));
        }
        return Map.copyOf(result);
    }
}

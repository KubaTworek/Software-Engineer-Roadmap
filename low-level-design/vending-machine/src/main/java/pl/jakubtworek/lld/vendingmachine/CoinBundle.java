package pl.jakubtworek.lld.vendingmachine;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Niemutowalny multizbiór monet: nominał -> liczba sztuk. Dwa zestawy o tej
 * samej wartości mogą być różne, np. PLN1 i dwie GR50. To ważne przy anulowaniu:
 * zwracamy depozyt, a nie przypadkową kombinację o równoważnej kwocie.
 *
 * Kopiujemy mapę wejściową i usuwamy zerowe wpisy, dzięki czemu równość opisuje
 * rzeczywiste monety. plus/minus tworzą nowy zestaw; nie niszczą dawnych widoków.
 */
public final class CoinBundle {

    private final Map<Coin, Integer> counts;
    private final Money total;

    public CoinBundle(Map<Coin, Integer> source) {
        Objects.requireNonNull(source, "source");
        var copy = new EnumMap<Coin, Integer>(Coin.class);
        long sum = 0;
        for (var entry : source.entrySet()) {
            Coin coin = Objects.requireNonNull(entry.getKey(), "coin");
            int count = Objects.requireNonNull(entry.getValue(), "count");
            if (count < 0) {
                throw new IllegalArgumentException("Liczba monet nie może być ujemna");
            }
            if (count > 0) {
                copy.put(coin, count);
            }
            // Rozszerzamy PRZED mnożeniem. Rzutowanie przepełnionego iloczynu
            // int na long nie odzyskałoby poprawnej wartości.
            sum = Math.addExact(sum, Math.multiplyExact((long) coin.grosz(), count));
        }
        // EnumMap służy budowie wyniku; copyOf nie obiecuje jej typu ani
        // kolejności. Płytka kopia wystarcza dla niemutowalnych Coin i Integer.
        counts = Map.copyOf(copy);
        total = new Money(sum);
    }

    public static CoinBundle empty() {
        return new CoinBundle(Map.of());
    }

    public static CoinBundle of(Coin... coins) {
        var counts = new EnumMap<Coin, Integer>(Coin.class);
        for (Coin coin : coins) {
            counts.merge(Objects.requireNonNull(coin, "coin"), 1, Math::addExact);
        }
        return new CoinBundle(counts);
    }

    public int count(Coin coin) {
        return counts.getOrDefault(Objects.requireNonNull(coin, "coin"), 0);
    }

    public Map<Coin, Integer> counts() {
        return counts;
    }

    public Money total() {
        return total;
    }

    public CoinBundle plus(CoinBundle other) {
        Objects.requireNonNull(other, "other");
        var result = new EnumMap<Coin, Integer>(Coin.class);
        result.putAll(counts);
        other.counts.forEach((coin, count) -> result.merge(coin, count, Math::addExact));
        return new CoinBundle(result);
    }

    public CoinBundle minus(CoinBundle other) {
        Objects.requireNonNull(other, "other");
        var result = new EnumMap<Coin, Integer>(Coin.class);
        result.putAll(counts);
        for (var entry : other.counts.entrySet()) {
            int current = count(entry.getKey());
            if (current < entry.getValue()) {
                throw new IllegalArgumentException("Brak wymaganych monet");
            }
            result.put(entry.getKey(), current - entry.getValue());
        }
        return new CoinBundle(result);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CoinBundle bundle && counts.equals(bundle.counts);
    }

    @Override
    public int hashCode() {
        return counts.hashCode();
    }

    @Override
    public String toString() {
        // Porządek prezentacji jest jawny; Map.copyOf nie obiecuje kolejności.
        return Arrays.stream(Coin.values()).filter(coin -> count(coin) > 0).map(coin -> coin.name() + "x" + count(coin))
                .collect(Collectors.joining(", ", "[", "]"));
    }
}

package pl.jakubtworek.lld.promotion.domain;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Największe reszty: najpierw całkowite części proporcjonalnych udziałów,
 * potem brakujące grosze według reszty malejąco i SKU rosnąco. Nigdy nie
 * zaokrąglamy każdej linii niezależnie, co mogłoby stworzyć lub zgubić grosz.
 * Wagi to AKTUALNE salda kwalifikowanych linii, nie ich ceny pierwotne.
 */
public final class DiscountAllocator {

    private record Share(String sku, long floor, BigInteger remainder) {
    }

    public Map<String, Money> allocate(Money amount, Map<String, Money> weights) {
        var copy = new TreeMap<>(Map.copyOf(weights));
        Money sum = Money.ZERO;
        for (Money value : copy.values()) sum = sum.plus(value);
        if (amount.compareTo(sum) > 0)
            throw new IllegalArgumentException("Alokacja przekracza podstawę");
        if (amount.cents() == 0)
            return Map.of();
        var shares = new ArrayList<Share>();
        long floors = 0;
        for (var entry : copy.entrySet()) {
            if (entry.getValue().cents() == 0)
                continue;
            BigInteger[] division = BigInteger.valueOf(amount.cents()).multiply(BigInteger.valueOf(entry.getValue()
                    .cents())).divideAndRemainder(BigInteger.valueOf(sum.cents()));
            long floor = division[0].longValueExact();
            floors = Math.addExact(floors, floor);
            shares.add(new Share(entry.getKey(), floor, division[1]));
        }
        shares.sort(Comparator.comparing(Share::remainder).reversed().thenComparing(Share::sku));
        int missing = Math.toIntExact(amount.cents() - floors);
        var result = new TreeMap<String, Money>();
        for (int i = 0; i < shares.size(); i++) {
            Share share = shares.get(i);
            long allocated = Math.addExact(share.floor(), i < missing ? 1 : 0);
            if (allocated > 0)
                result.put(share.sku(), new Money(allocated));
        }
        return Map.copyOf(result);
    }
}

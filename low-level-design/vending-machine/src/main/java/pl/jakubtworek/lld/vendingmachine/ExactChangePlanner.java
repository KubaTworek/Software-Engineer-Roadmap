package pl.jakubtworek.lld.vendingmachine;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Objects;
import java.util.Optional;

/**
 * Dokładna reszta przy ograniczonej liczbie każdego nominału. Zachłanne branie
 * największej monety może zawieść: dla 60 gr i monet 50,20,20,20 trzeba wybrać
 * trzy dwudziestki, a nie pięćdziesiątkę. Szukamy dowolnego rozwiązania;
 * deterministyczny wynik nie jest obietnicą minimalnej liczby monet.
 *
 * Algorytm to bounded knapsack z informacją o pozostałej liczbie bieżącej
 * monety. Dla D nominałów i kwoty A ma czas oraz pamięć O(D*A), niezależne
 * od ogromnej liczby sztuk w kasecie. Jawny limit A chroni rozmiar tablic.
 */
public final class ExactChangePlanner implements ChangePolicy {

    public static final int MAX_AMOUNT_GROSZ = ChangePolicy.MAX_AMOUNT_GROSZ;
    private static final Coin[] COINS = Arrays.stream(Coin.values()).sorted(Comparator.comparingInt(Coin::grosz)
            .reversed()).toArray(Coin[]::new);

    @Override
    public Optional<CoinBundle> plan(Money amount, CoinBundle available) {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(available, "available");
        if (amount.grosz() > MAX_AMOUNT_GROSZ) {
            throw new IllegalArgumentException("Kwota przekracza limit planowania reszty");
        }
        int target = Math.toIntExact(amount.grosz());
        if (target == 0) {
            return Optional.of(CoinBundle.empty());
        }
        // Warunek konieczny sprawdzamy przed alokacją tablic. Odwrotność nie
        // zachodzi: moneta 50 gr ma wartość >=30 gr, ale nie daje reszty 30 gr.
        if (amount.grosz() > available.total().grosz()) {
            return Optional.empty();
        }
        boolean[] reachable = new boolean[target + 1];
        reachable[0] = true;
        int[][] used = new int[COINS.length][target + 1];
        for (int index = 0; index < COINS.length; index++) {
            Coin coin = COINS[index];
            int[] remaining = new int[target + 1];
            Arrays.fill(remaining, -1);
            for (int value = 0; value <= target; value++) {
                if (reachable[value]) {
                    // Kwotę dało się uzyskać wcześniejszymi nominałami:
                    // nie użyliśmy jeszcze żadnej monety bieżącego rodzaju.
                    remaining[value] = available.count(coin);
                } else if (value >= coin.grosz() && remaining[value - coin.grosz()] > 0) {
                    // Ta kwota powstaje przez dokładanie TEGO nominału.
                    // Licznik remaining zapobiega użyciu jednej monety wielokrotnie.
                    remaining[value] = remaining[value - coin.grosz()] - 1;
                    used[index][value] = used[index][value - coin.grosz()] + 1;
                }
            }
            for (int value = 0; value <= target; value++) {
                reachable[value] = remaining[value] >= 0;
            }
        }
        if (!reachable[target]) {
            return Optional.empty();
        }
        // Cofamy się przez nominały. used mówi, ile sztuk dodała dana warstwa;
        // pozostała kwota musi należeć do poprzedniej warstwy. Nie zwracamy
        // samego true: automat potrzebuje konkretnego zestawu do odjęcia z kasety.
        var result = new EnumMap<Coin, Integer>(Coin.class);
        int left = target;
        for (int index = COINS.length - 1; index >= 0; index--) {
            int count = used[index][left];
            if (count > 0) {
                result.put(COINS[index], count);
            }
            left -= count * COINS[index].grosz();
        }
        if (left != 0) {
            throw new IllegalStateException("Niespójna rekonstrukcja reszty");
        }
        return Optional.of(new CoinBundle(result));
    }
}

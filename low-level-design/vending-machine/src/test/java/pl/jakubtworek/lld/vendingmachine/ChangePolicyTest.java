package pl.jakubtworek.lld.vendingmachine;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.vendingmachine.Coin.*;

/**
 * Te same wymagania dla dwóch strategii są wykonywalnym kontraktem podstawienia.
 * Wyrocznia rekurencyjnie enumeruje kombinacje monet: nie kopiuje żadnego DP.
 * Minimalność sprawdzamy tylko dla strategii, która ją obiecuje. Nie wymuszamy
 * konkretnego zestawu przy remisie, bo nie należy on do kontraktu.
 */
class ChangePolicyTest {

    static Stream<ChangePolicy> policies() {
        return Stream.of(new ExactChangePlanner(), new MinimumCoinChangePlanner());
    }

    @ParameterizedTest
    @MethodSource("policies")
    void validatesLimitsAndDistinguishesInsufficientValueFromImpossibleComposition(ChangePolicy policy) {
        assertThrows(NullPointerException.class, () -> policy.plan(null, CoinBundle.empty()));
        assertThrows(NullPointerException.class, () -> policy.plan(new Money(0), null));
        // Limit sprawdzamy także przy pustej kasecie: szybka odmowa nie może
        // zmienić niepoprawnego argumentu w zwykły brak rozwiązania.
        assertThrows(IllegalArgumentException.class, () -> policy.plan(new Money(10001), CoinBundle.empty()));
        assertEquals(CoinBundle.empty(), policy.plan(new Money(0), CoinBundle.empty()).orElseThrow());
        assertTrue(policy.plan(new Money(60), CoinBundle.of(GR50)).isEmpty());
        assertTrue(policy.plan(new Money(30), CoinBundle.of(GR50)).isEmpty());
        assertEquals(new CoinBundle(Map.of(GR20, 3)), policy.plan(new Money(60), new CoinBundle(Map.of(GR50, 1, GR20,
                3))).orElseThrow());
        var huge = new CoinBundle(Map.of(GR1, Integer.MAX_VALUE));
        assertEquals(10000, policy.plan(new Money(10000), huge).orElseThrow().count(GR1));
    }

    @ParameterizedTest
    @MethodSource("policies")
    void everySubsetOfAllNineDenominationsAgreesWithIndependentMinimum(ChangePolicy policy) {
        // 2^9 kaset po 0/1 sztuce. Badamy stały zestaw małych kwot i granice
        // nominałów. To przekrój wszystkich kaset binarnych, lecz nie wszystkich
        // kwot każdej kasety ani wszystkich możliwych liczności.
        Coin[] coins = Coin.values();
        for (int mask = 0; mask < (1 << coins.length); mask++) {
            var counts = new EnumMap<Coin, Integer>(Coin.class);
            for (int index = 0; index < coins.length; index++) {
                counts.put(coins[index], (mask >>> index) & 1);
            }
            var available = new CoinBundle(counts);
            int[] minimum = enumeratedMinimum(available);
            // Wszystkie kwoty 0..20 oraz punkty z wyższych nominałów. Pełna
            // tablica wyroczni jest niezależna od próbkowania zapytań planera.
            int[] amounts = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 49, 50, 51, 99,
                    100, 101, 199, 200, 201, 499, 500, 501, 888, 889 };
            for (int amount : amounts) {
                check(policy, amount, available, minimum);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("policies")
    void repeatedCoinsAndAddingResourcesPreserveFeasibility(ChangePolicy policy) {
        var random = new Random(20261019L);
        for (int sample = 0; sample < 160; sample++) {
            var counts = new EnumMap<Coin, Integer>(Coin.class);
            int target = 0;
            // Każdy nominał ma 0..2 sztuki. Niezależnie wybieramy podzbiór,
            // więc target ma świadectwo wykonalności jeszcze przed plannerem.
            for (Coin coin : Coin.values()) {
                int count = random.nextInt(3);
                counts.put(coin, count);
                target += coin.grosz() * random.nextInt(count + 1);
            }
            var available = new CoinBundle(counts);
            int[] expected = enumeratedMinimum(available);
            check(policy, target, available, expected);
            check(policy, random.nextInt(1777), available, expected);
            var extra = CoinBundle.of(Coin.values()[sample % Coin.values().length]);
            var expanded = available.plus(extra);
            var before = policy.plan(new Money(target), available).orElseThrow();
            var after = policy.plan(new Money(target), expanded).orElseThrow();
            assertEquals(new Money(target), after.total());
            assertDoesNotThrow(() -> expanded.minus(after));
            if (policy instanceof MinimumCoinChangePlanner) {
                assertTrue(numberOfCoins(after) <= numberOfCoins(before),
                        "Więcej zasobów nie pogarsza optimum dla tej samej kwoty");
            }
            assertEquals(new CoinBundle(counts), available, "Planowanie nie zmienia źródła");
        }
    }

    private static void check(ChangePolicy policy, int amount, CoinBundle available, int[] expected) {
        int minimum = amount < expected.length ? expected[amount] : Integer.MAX_VALUE;
        var result = policy.plan(new Money(amount), available);
        String context = policy.getClass().getSimpleName() + ", kwota=" + amount + ", zasób=" + available;
        assertEquals(minimum != Integer.MAX_VALUE, result.isPresent(), context);
        if (result.isPresent()) {
            var change = result.orElseThrow();
            assertEquals(new Money(amount), change.total(), context);
            assertDoesNotThrow(() -> available.minus(change), context);
            if (policy instanceof MinimumCoinChangePlanner) {
                assertEquals(minimum, numberOfCoins(change), context);
            }
        }
    }

    private static int numberOfCoins(CoinBundle bundle) {
        return bundle.counts().values().stream().mapToInt(Integer::intValue).sum();
    }

    private static int[] enumeratedMinimum(CoinBundle available) {
        int[] minimum = new int[Math.toIntExact(available.total().grosz()) + 1];
        Arrays.fill(minimum, Integer.MAX_VALUE);
        enumerate(Coin.values(), available, 0, 0, 0, minimum);
        return minimum;
    }

    // Każdy liść to jeden konkretny zestaw: zapisujemy najlepszą liczbę monet
    // dla jego sumy. Przy 0..2 sztukach mamy najwyżej 3^9=19683 liście.
    private static void enumerate(Coin[] coins, CoinBundle available, int index, int sum, int count, int[] minimum) {
        if (index == coins.length) {
            minimum[sum] = Math.min(minimum[sum], count);
            return;
        }
        Coin coin = coins[index];
        for (int used = 0; used <= available.count(coin); used++) {
            enumerate(coins, available, index + 1, sum + used * coin.grosz(), count + used, minimum);
        }
    }
}

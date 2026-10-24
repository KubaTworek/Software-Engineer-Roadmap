package pl.jakubtworek.lld.vendingmachine;

import org.junit.jupiter.api.Test;
import java.util.EnumMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.vendingmachine.Coin.*;

/**
 * Wyrocznia enumeruje zestawy monet, a nie kopiuje dynamiczne programowanie.
 * Sprawdzamy istnienie oraz poprawność konkretnego zwrotu, bez wymuszania
 * minimalnej liczby monet, której publiczny kontrakt nie obiecuje.
 */
class ExactChangePlannerTest {

    private final ExactChangePlanner planner = new ExactChangePlanner();

    @Test
    void findsSolutionThatGreedyLargestFirstMisses() {
        var available = new CoinBundle(Map.of(GR50, 1, GR20, 3));
        var change = planner.plan(new Money(60), available).orElseThrow();
        assertEquals(new CoinBundle(Map.of(GR20, 3)), change);
        assertEquals(1, available.count(GR50));
    }

    @Test
    void amountAloneDoesNotGuaranteeExactChangeAndOneCoinCannotBeReused() {
        assertTrue(planner.plan(new Money(40), CoinBundle.of(GR20)).isEmpty());
        assertTrue(planner.plan(new Money(30), CoinBundle.of(GR50, PLN1)).isEmpty());
        assertTrue(planner.plan(new Money(200), CoinBundle.of(PLN1)).isEmpty());
        assertEquals(CoinBundle.empty(), planner.plan(new Money(0), CoinBundle.empty()).orElseThrow());
    }

    @Test
    void largeCoinCountDoesNotRequireLoopingOverEveryPhysicalCoin() {
        var available = new CoinBundle(Map.of(GR1, Integer.MAX_VALUE));
        var result = planner.plan(new Money(10000), available).orElseThrow();
        assertEquals(10000, result.count(GR1));
        assertThrows(IllegalArgumentException.class, () -> planner.plan(new Money(10001), available));
    }

    @Test
    void allSmallInventoriesAndAmountsAgreeWithIndependentEnumeration() {
        Coin[] coins = { GR1, GR2, GR5, GR10 };
        // 3^4=81 zasobów (0..2 sztuki nominału), każdy z 41 kwotami 0..40.
        // To 3321 sprawdzeń wewnątrz jednego testu, nie dowód dla każdej kasety.
        for (int configuration = 0; configuration < 81; configuration++) {
            var counts = new EnumMap<Coin, Integer>(Coin.class);
            int[] limit = new int[4];
            int encoding = configuration;
            for (int index = 0; index < 4; index++) {
                limit[index] = encoding % 3;
                counts.put(coins[index], limit[index]);
                encoding /= 3;
            }
            boolean[] expected = new boolean[41];
            for (int a = 0; a <= limit[0]; a++) {
                for (int b = 0; b <= limit[1]; b++) {
                    for (int c = 0; c <= limit[2]; c++) {
                        for (int d = 0; d <= limit[3]; d++) {
                            expected[a + b * 2 + c * 5 + d * 10] = true;
                        }
                    }
                }
            }
            var available = new CoinBundle(counts);
            for (int amount = 0; amount <= 40; amount++) {
                var result = planner.plan(new Money(amount), available);
                assertEquals(expected[amount], result.isPresent(),
                        "Konfiguracja=" + configuration + ", kwota=" + amount);
                if (result.isPresent()) {
                    var change = result.orElseThrow();
                    assertEquals(new Money(amount), change.total());
                    for (Coin coin : Coin.values()) {
                        assertTrue(change.count(coin) <= available.count(coin));
                    }
                }
            }
        }
    }
}

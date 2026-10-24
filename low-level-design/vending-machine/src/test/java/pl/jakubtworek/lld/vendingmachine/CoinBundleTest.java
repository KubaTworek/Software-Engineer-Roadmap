package pl.jakubtworek.lld.vendingmachine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.vendingmachine.Coin.*;

/**
 * Wartość pieniędzy i zestaw nominałów mają różne reguły równości. Te testy
 * chronią dokładność, własność mapy i arytmetykę, na których opiera się zwrot.
 */
class CoinBundleTest {

    @Test
    void equalAmountDoesNotMeanSameCoinsAndZeroCountsAreNormalized() {
        var one = CoinBundle.of(PLN1);
        var two = CoinBundle.of(GR50, GR50);
        assertEquals(one.total(), two.total());
        assertNotEquals(one, two);
        assertEquals(CoinBundle.empty(), new CoinBundle(Map.of(PLN1, 0)));
        assertEquals(CoinBundle.of(PLN1), new CoinBundle(Map.of(PLN1, 1, GR50, 0)));
    }

    @Test
    void sourceAndReturnedMapsCannotMutateBundle() {
        var source = new EnumMap<Coin, Integer>(Coin.class);
        source.put(PLN1, 2);
        var bundle = new CoinBundle(source);
        source.clear();
        assertEquals(new Money(200), bundle.total());
        assertThrows(UnsupportedOperationException.class, () -> bundle.counts().clear());
    }

    @Test
    void additionAndSubtractionPreserveOldValuesAndRequireActualCoins() {
        var original = CoinBundle.of(PLN1, GR50);
        var combined = original.plus(CoinBundle.of(GR50));
        assertEquals(CoinBundle.of(PLN1, GR50, GR50), combined);
        assertEquals(original, combined.minus(CoinBundle.of(GR50)));
        assertEquals(CoinBundle.of(PLN1, GR50), original);
        // Wartość wystarcza, ale nie ma dwóch konkretnych monet GR50.
        assertThrows(IllegalArgumentException.class, () -> original.minus(CoinBundle.of(GR50, GR50)));
    }

    @Test
    void invalidCountsAndOverflowAreErrorsNotNegativeCash() {
        assertThrows(IllegalArgumentException.class, () -> new CoinBundle(Map.of(GR1, -1)));
        var maximum = new CoinBundle(Map.of(GR1, Integer.MAX_VALUE));
        assertThrows(ArithmeticException.class, () -> maximum.plus(CoinBundle.of(GR1)));
        assertEquals(Integer.MAX_VALUE, maximum.count(GR1));
        assertThrows(ArithmeticException.class, () -> new Money(Long.MAX_VALUE).plus(new Money(1)));
        assertThrows(IllegalArgumentException.class, () -> new Money(0).minus(new Money(1)));
        assertEquals("5.01 PLN", new Money(501).toString());
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 3, 30, 300, 1000, -1 })
    void unsupportedDenominationIsDifferentFromMonetaryAmount(int grosz) {
        assertThrows(IllegalArgumentException.class, () -> Coin.fromGrosz(grosz));
    }

    @Test
    void configurationRejectsImpossibleValuesAndDuplicateSlotIdentity() {
        assertEquals(new SlotId("A1"), new SlotId(" a1 "));
        assertThrows(IllegalArgumentException.class, () -> new SlotId("A 1"));
        assertThrows(IllegalArgumentException.class, () -> new Product("", new Money(1)));
        assertThrows(IllegalArgumentException.class, () -> new Product("P", new Money(0)));
        var product = new Product("P", new Money(1));
        assertThrows(IllegalArgumentException.class, () -> new StockItem(new SlotId("A1"), product, -1));
        assertThrows(IllegalArgumentException.class, () -> new VendingMachine(List.of(), CoinBundle.empty()));
        assertThrows(IllegalArgumentException.class, () -> new VendingMachine(List.of(new StockItem(new SlotId("A1"),
                product, 1), new StockItem(new SlotId(" a1 "), product, 2)), CoinBundle.empty()));
        assertThrows(IllegalArgumentException.class, () -> Fixtures.machine(10001, 1, CoinBundle.empty()));
        assertThrows(IllegalArgumentException.class, () -> new SaleReceipt(new SlotId("A1"), product,
                CoinBundle.of(PLN1), CoinBundle.empty()));
    }
}

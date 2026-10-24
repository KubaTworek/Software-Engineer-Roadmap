package pl.jakubtworek.lld.promotion.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Granice grosza i progu są ważniejsze niż testowanie getterów. Dla procentów
 * używamy BigDecimal jako wyroczni odmiennej od produkcyjnego iloczynu BigInteger.
 */
class ValuesAndConditionsTest {

    @Test
    void decimalMoneyIsExactAndArithmeticDoesNotWrap() {
        assertEquals(Money.pln("0.30"), Money.pln("0.10").plus(Money.pln("0.20")));
        assertEquals(new Money(Long.MAX_VALUE), Money.pln("92233720368547758.07"));
        assertEquals("12.30 PLN", Money.pln("12.3").toString());
        assertThrows(ArithmeticException.class, () -> new Money(Long.MAX_VALUE).plus(new Money(1)));
        assertThrows(ArithmeticException.class, () -> new CartLine("A", "books", new Money(Long.MAX_VALUE), 2));
        assertThrows(ArithmeticException.class, () -> Money.pln("92233720368547758.08"));
        assertThrows(IllegalArgumentException.class, () -> Money.ZERO.minus(new Money(1)));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "-1", "1.001", "NaN", "1,23", "1e2", " 1", "+1" })
    void malformedAmountsAreRejected(String text) {
        assertThrows(IllegalArgumentException.class, () -> Money.pln(text));
    }

    @ParameterizedTest
    @CsvSource({ "1,5000,1", "1,4999,0", "3,5000,2", "100,1250,13", "1999,1250,250", "0,10000,0", "7,10000,7",
            "99,0,0" })
    void percentRoundsDiscountOnceHalfUp(long cents, int basisPoints, long expected) {
        assertEquals(new Money(expected), new Percent(basisPoints).of(new Money(cents)));
    }

    @Test
    void percentageIntermediateDoesNotOverflowAtMaximumMoney() {
        Money maximum = new Money(Long.MAX_VALUE);
        for (int rate : new int[] { 1, 3333, 5000, 9999, 10000 }) {
            long expected = BigDecimal.valueOf(maximum.cents()).multiply(BigDecimal.valueOf(rate))
                    .divide(BigDecimal.valueOf(10000), 0, RoundingMode.HALF_UP).longValueExact();
            assertEquals(new Money(expected), new Percent(rate).of(maximum));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { -1, 10001, Integer.MAX_VALUE })
    void percentageIsBounded(int rate) {
        assertThrows(IllegalArgumentException.class, () -> new Percent(rate));
    }

    @Test
    void percentageTextAndPositiveDiscountConfigurationHaveDifferentContracts() {
        assertEquals(new Percent(1250), Percent.of("12.50"));
        assertThrows(IllegalArgumentException.class, () -> Percent.of("100.01"));
        assertThrows(IllegalArgumentException.class, () -> Percent.of("0.001"));
        assertThrows(IllegalArgumentException.class, () -> new PercentageDiscount(new Percent(0)));
        assertThrows(IllegalArgumentException.class, () -> new FixedDiscount(Money.ZERO));
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, -1, Integer.MIN_VALUE })
    void quantitiesMustBePositive(int quantity) {
        assertThrows(IllegalArgumentException.class, () -> new CartLine("A", "books", Money.pln("1"), quantity));
    }

    @Test
    void duplicateSkuAndOverflowingBasketCannotBeConstructed() {
        var line = new CartLine("A", "books", Money.pln("10"), 1);
        assertThrows(IllegalArgumentException.class, () -> new Basket(List.of(line, line)));
        assertThrows(ArithmeticException.class, () -> new Basket(List.of(new CartLine("A", "books",
                new Money(Long.MAX_VALUE), 1), new CartLine("B", "books", new Money(1), 1))));
        assertThrows(IllegalArgumentException.class, () -> new CartLine(" ", "books", Money.ZERO, 1));
        assertThrows(IllegalArgumentException.class, () -> new CartLine("A", " ", Money.ZERO, 1));
    }

    @Test
    void conditionsReadOriginalFactsAndRespectInclusiveThreshold() {
        var context = new PricingContext(new Basket(List.of(new CartLine("A", "books", Money.pln("50"), 2))),
                CustomerTier.VIP, Set.of(" welcome "));
        assertTrue(Conditions.minimumSubtotal(Money.pln("100")).matches(context));
        assertFalse(Conditions.minimumSubtotal(Money.pln("100.01")).matches(context));
        assertTrue(Conditions.allOf(Conditions.tier(CustomerTier.VIP), Conditions.category("books"),
                Conditions.coupon("WELCOME")).matches(context));
        // Kategorie są case-sensitive, kody kuponów nie.
        assertFalse(Conditions.category("BOOKS").matches(context));
        assertTrue(Conditions.anyOf(Conditions.tier(CustomerTier.STANDARD), Conditions.coupon("welcome"))
                .matches(context));
        assertFalse(Conditions.allOf(Conditions.tier(CustomerTier.STANDARD), Conditions.always()).matches(context));
        assertFalse(Conditions.anyOf(Conditions.tier(CustomerTier.STANDARD), Conditions.coupon("MISSING"))
                .matches(context));
    }

    @Test
    void inputsAndCompositeChildrenAreDefensivelyCopied() {
        var source = new ArrayList<>(List.of(new CartLine("A", "books", Money.pln("1"), 1)));
        var coupons = new HashSet<>(Set.of("WELCOME"));
        Basket basket = new Basket(source);
        var context = new PricingContext(basket, CustomerTier.STANDARD, coupons);
        source.clear();
        coupons.clear();
        assertEquals(Money.pln("1"), basket.subtotal());
        assertTrue(Conditions.coupon("WELCOME").matches(context));
        assertThrows(UnsupportedOperationException.class, () -> basket.lines().clear());
        assertThrows(UnsupportedOperationException.class, () -> context.coupons().clear());
        Condition[] array = { Conditions.always() };
        Condition condition = Conditions.allOf(array);
        array[0] = Conditions.coupon("MISSING");
        assertTrue(condition.matches(context));
    }

    @Test
    void invalidCompositionsAndCodesFailAtConfigurationBoundary() {
        assertThrows(IllegalArgumentException.class, Conditions::allOf);
        assertThrows(IllegalArgumentException.class, Conditions::anyOf);
        assertThrows(IllegalArgumentException.class, () -> Conditions.coupon(" "));
        assertThrows(IllegalArgumentException.class, () -> Conditions.category("a b"));
        assertThrows(IllegalArgumentException.class, () -> new PromotionId("lowercase"));
        assertThrows(IllegalArgumentException.class, () -> new PromotionId(""));
    }
}

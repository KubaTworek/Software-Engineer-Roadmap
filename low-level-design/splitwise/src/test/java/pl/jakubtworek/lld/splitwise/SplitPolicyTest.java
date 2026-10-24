package pl.jakubtworek.lld.splitwise;

import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.splitwise.Fixtures.*;

/**
 * Testy objaśniają różnicę między zachowaniem kwoty a niezależnym zaokrągleniem
 * udziałów. Procenty sprawdza wyrocznia enumerująca wszystkie całkowite podziały,
 * a nie druga implementacja sortowania największych reszt.
 */
class SplitPolicyTest {

    @Test
    void equalSplitAssignsLeftoverCentsByCodeRegardlessOfInputOrder() {
        var policy = new EqualSplit(List.of(U3, U1, U2));
        assertEquals(Map.of(U1, new Money(34), U2, new Money(33), U3, new Money(33)), policy.split(new Money(100)));
        assertEquals(Map.of(U1, new Money(1), U2, new Money(0), U3, new Money(0)), policy.split(new Money(1)));
        assertEquals(policy.split(new Money(100)), new EqualSplit(IDS).split(new Money(100)));
    }

    @Test
    void equalSplitPreservesSumFor1505SmallCases() {
        for (int count = 1; count <= 5; count++) {
            var members = new ArrayList<MemberId>();
            for (int index = 0; index < count; index++) {
                members.add(new MemberId("U" + index));
            }
            var policy = new EqualSplit(members);
            for (int amount = 0; amount <= 300; amount++) {
                var result = policy.split(new Money(amount));
                assertEquals(amount, result.values().stream().mapToLong(Money::grosz).sum());
                long min = result.values().stream().mapToLong(Money::grosz).min().orElseThrow();
                long max = result.values().stream().mapToLong(Money::grosz).max().orElseThrow();
                assertTrue(max - min <= 1);
            }
        }
    }

    @Test
    void percentageSplitUsesLargestFractionAndCodeForTies() {
        var policy = new PercentageSplit(Map.of(U1, 3333, U2, 3333, U3, 3334));
        assertEquals(Map.of(U1, new Money(33), U2, new Money(33), U3, new Money(34)), policy.split(new Money(100)));
        assertEquals(Map.of(U1, new Money(0), U2, new Money(0), U3, new Money(1)), policy.split(new Money(1)));
        assertEquals(Map.of(U1, new Money(1), U2, new Money(0)), new PercentageSplit(Map.of(U2, 5000, U1, 5000))
                .split(new Money(1)));
    }

    @Test
    void all244SmallPercentageCasesMatchIndependentEnumeration() {
        int[][] weights = { { 3333, 3333, 3334 }, { 1, 4999, 5000 }, { 1250, 1250, 7500 }, { 2500, 2500, 5000 } };
        for (int[] points : weights) {
            var policy = new PercentageSplit(Map.of(U1, points[0], U2, points[1], U3, points[2]));
            for (int amount = 0; amount <= 60; amount++) {
                int[] expected = enumerateBest(amount, points);
                var result = policy.split(new Money(amount));
                for (int i = 0; i < 3; i++) {
                    assertEquals(new Money(expected[i]), result.get(IDS.get(i)));
                }
            }
        }
    }

    // Przeglądamy WSZYSTKIE a+b+c=amount. Najmniejszy łączny błąd względem
    // idealnych udziałów daje największe reszty; przy równym błędzie wybieramy
    // leksykograficznie większy wektor udziałów (grosz wcześniej dla mniejszego ID).
    private static int[] enumerateBest(int amount, int[] weights) {
        long bestError = Long.MAX_VALUE;
        int[] best = null;
        for (int a = amount; a >= 0; a--) {
            for (int b = amount - a; b >= 0; b--) {
                int c = amount - a - b;
                long error = Math.abs(a * 10000L - amount * (long) weights[0]) + Math.abs(b * 10000L - amount * (long) weights[1]) + Math.abs(c * 10000L - amount * (long) weights[2]);
                if (error < bestError) {
                    bestError = error;
                    best = new int[] { a, b, c };
                }
            }
        }
        return best;
    }

    @Test
    void longMaximumAmountDoesNotOverflowIntermediatePercentageProduct() {
        for (SplitPolicy policy : List.of(new EqualSplit(IDS), new PercentageSplit(Map.of(U1, 3333, U2, 3333, U3,
                3334)))) {
            var result = policy.split(new Money(Long.MAX_VALUE));
            assertEquals(BigInteger.valueOf(Long.MAX_VALUE), result.values().stream()
                    .map(value -> BigInteger.valueOf(value.grosz())).reduce(BigInteger.ZERO, BigInteger::add));
        }
    }

    @Test
    void sourceCollectionsAndOrderCannotChangeConfiguredPolicies() {
        var participants = new ArrayList<>(IDS);
        var equal = new EqualSplit(participants);
        participants.clear();
        var weights = new LinkedHashMap<MemberId, Integer>();
        weights.put(U3, 5000);
        weights.put(U2, 2500);
        weights.put(U1, 2500);
        var percent = new PercentageSplit(weights);
        weights.clear();
        assertEquals(3, equal.split(new Money(1)).size());
        assertEquals(new PercentageSplit(Map.of(U1, 2500, U2, 2500, U3, 5000)).split(new Money(7)),
                percent.split(new Money(7)));
        assertThrows(UnsupportedOperationException.class, () -> percent.split(new Money(1)).clear());
        assertThrows(UnsupportedOperationException.class, () -> equal.split(new Money(1)).clear());
    }

    @Test
    void invalidConfigurationAndNullInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new EqualSplit(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new EqualSplit(List.of(U1, U1)));
        assertThrows(IllegalArgumentException.class, () -> new PercentageSplit(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new PercentageSplit(Map.of(U1, 9999)));
        assertThrows(IllegalArgumentException.class, () -> new PercentageSplit(Map.of(U1, 10001)));
        assertThrows(IllegalArgumentException.class, () -> new PercentageSplit(Map.of(U1, 10000, U2, 0)));
        assertThrows(NullPointerException.class, () -> new EqualSplit(null));
        assertThrows(NullPointerException.class, () -> new PercentageSplit(null));
        assertThrows(NullPointerException.class, () -> new EqualSplit(IDS).split(null));
    }
}

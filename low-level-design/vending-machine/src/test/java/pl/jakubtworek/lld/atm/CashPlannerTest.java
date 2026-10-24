package pl.jakubtworek.lld.atm;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.atm.Banknote.*;

/**
 * Wyrocznia enumeruje ilości banknotów, zamiast powielać tablice i rekonstrukcję DP.
 */
class CashPlannerTest {

    @Test
    void all1280SmallStockAndAmountCasesMatchIndependentEnumeration() {
        var planner = new BoundedCashPlanner();
        int checked = 0;
        for (int tens = 0; tens <= 3; tens++) {
            for (int twenties = 0; twenties <= 3; twenties++) {
                for (int fifties = 0; fifties <= 3; fifties++) {
                    var stock = new CashInventory(Map.of(PLN_10, tens, PLN_20, twenties, PLN_50, fifties));
                    for (int units = 1; units <= 20; units++) {
                        int best = Integer.MAX_VALUE;
                        for (int a = 0; a <= tens; a++) {
                            for (int b = 0; b <= twenties; b++) {
                                for (int c = 0; c <= fifties; c++) {
                                    if (a + b * 2 + c * 5 == units) {
                                        best = Math.min(best, a + b + c);
                                    }
                                }
                            }
                        }
                        var amount = new Money(units * 1000L);
                        var plan = planner.plan(amount, stock);
                        assertEquals(best != Integer.MAX_VALUE, plan.isPresent());
                        if (plan.isPresent()) {
                            var bundle = plan.orElseThrow();
                            assertEquals(best, bundle.count());
                            assertEquals(amount, bundle.total());
                            bundle.notes().forEach((note, count) -> assertTrue(count <= stock.count(note)));
                            assertEquals(stock.total().minus(amount), stock.without(bundle).total());
                        }
                        checked++;
                    }
                }
            }
        }
        assertEquals(1280, checked);
    }

    @Test
    void boundedCombinationSucceedsWhereLargestNoteGreedyWouldGetStuck() {
        var stock = new CashInventory(Map.of(PLN_50, 1, PLN_20, 3));
        var plan = new BoundedCashPlanner().plan(new Money(6000), stock).orElseThrow();
        assertEquals(Map.of(PLN_20, 3), plan.notes());
        assertEquals(1, stock.count(PLN_50));
    }

    @Test
    void maximumWithdrawalAndHugeStockDoNotExpandLoopsByStockSize() {
        var stock = new CashInventory(Map.of(PLN_200, Integer.MAX_VALUE, PLN_10, Integer.MAX_VALUE));
        var plan = new BoundedCashPlanner().plan(WithdrawalRules.MAX_AMOUNT, stock).orElseThrow();
        assertEquals(Map.of(PLN_200, 10), plan.notes());
        assertEquals(20000L * Integer.MAX_VALUE + 1000L * Integer.MAX_VALUE, stock.total().grosz());
    }

    @Test
    void invalidAmountsAndInsufficientDenominationsAreDistinct() {
        var planner = new BoundedCashPlanner();
        var stock = new CashInventory(Map.of(PLN_50, 2));
        assertTrue(planner.plan(new Money(6000), stock).isEmpty());
        for (long amount : new long[] { 0, 1, 999, 1001, 200001, 201000 }) {
            assertThrows(AtmException.class, () -> planner.plan(new Money(amount), stock));
        }
        assertThrows(NullPointerException.class, () -> planner.plan(null, stock));
        assertThrows(NullPointerException.class, () -> planner.plan(new Money(1000), null));
    }
}

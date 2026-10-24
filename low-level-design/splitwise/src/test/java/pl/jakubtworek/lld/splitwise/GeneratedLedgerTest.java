package pl.jakubtworek.lld.splitwise;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.splitwise.Fixtures.*;
import static pl.jakubtworek.lld.splitwise.SplitException.Reason.*;

/**
 * Model sald używa BigInteger i zmian z poleceń, nie produkcyjnego replay.
 * Kwoty losowych wydatków są podzielne przez 3 lub 4, aby wyrocznia nie kopiowała
 * algorytmu groszowych reszt: ten ma oddzielny przegląd małych podziałów.
 * 1200 poleceń obejmuje także odmowy i ponowną kontrolę dawnych publikacji.
 */
class GeneratedLedgerTest {

    private record Archive(GroupSnapshot view, List<BigInteger> balances, int entries) {
    }

    @ParameterizedTest
    @ValueSource(longs = { 17, 71, 213, 901 })
    void generatedExpensesAndSettlementsPreserveIndependentBalancesAndOldViews(long seed) {
        var group = group();
        BigInteger[] model = { BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO };
        var archives = new ArrayList<Archive>();
        var identifiers = new HashSet<EntryId>();
        var random = new Random(seed);
        int entries = 0;
        for (int step = 0; step < 300; step++) {
            var before = group.snapshot();
            if (step == 0 || (step > 2 && random.nextBoolean())) {
                boolean equal = step == 0 || random.nextBoolean();
                int factor = step == 0 ? 100 : random.nextInt(51);
                int payer = step == 0 ? 0 : random.nextInt(3);
                long total = factor * (equal ? 3L : 4L);
                SplitPolicy policy = equal ? new EqualSplit(IDS) : new PercentageSplit(Map.of(U1, 2500, U2, 2500, U3,
                        5000));
                if (total == 0) {
                    assertEquals(INVALID_AMOUNT, assertThrows(SplitException.class,
                            () -> group.addExpense(IDS.get(payer), new Money(0), policy, "Zero")).reason());
                    assertSame(before, group.snapshot());
                } else {
                    var expense = group.addExpense(IDS.get(payer), new Money(total), policy, "Próbka");
                    assertTrue(identifiers.add(expense.id()));
                    entries++;
                    model[payer] = model[payer].add(BigInteger.valueOf(total));
                    for (int member = 0; member < 3; member++) {
                        long share = equal ? factor : member == 2 ? 2L * factor : factor;
                        assertEquals(new Money(share), expense.shares().get(IDS.get(member)));
                        model[member] = model[member].subtract(BigInteger.valueOf(share));
                    }
                }
            } else {
                int from, to;
                long amount;
                if (step == 1 || step == 2) {
                    from = step;
                    to = 0;
                    amount = 100;
                } else {
                    var debtors = new ArrayList<Integer>();
                    var creditors = new ArrayList<Integer>();
                    for (int i = 0; i < 3; i++) {
                        if (model[i].signum() < 0) {
                            debtors.add(i);
                        }
                        if (model[i].signum() > 0) {
                            creditors.add(i);
                        }
                    }
                    if (!debtors.isEmpty() && !creditors.isEmpty() && random.nextInt(4) != 0) {
                        from = debtors.get(random.nextInt(debtors.size()));
                        to = creditors.get(random.nextInt(creditors.size()));
                        int cap = model[from].negate().min(model[to]).min(BigInteger.valueOf(200)).intValueExact();
                        // Obejmuje zero oraz przekroczenie limitu o grosz.
                        amount = random.nextInt(cap + 2);
                    } else {
                        from = random.nextInt(3);
                        to = random.nextInt(3);
                        amount = random.nextInt(301);
                    }
                }
                var expected = amount == 0 ? INVALID_AMOUNT : from == to ? SELF_SETTLEMENT : model[from].signum() >= 0
                        || model[to].signum() <= 0 ? INVALID_SETTLEMENT_DIRECTION : BigInteger.valueOf(amount)
                        .compareTo(model[from].negate()) > 0 || BigInteger.valueOf(amount)
                        .compareTo(model[to]) > 0 ? OVERPAYMENT : null;
                if (expected != null) {
                    assertEquals(expected, assertThrows(SplitException.class, () -> group.settle(IDS.get(from),
                            IDS.get(to), new Money(amount))).reason());
                    assertSame(before, group.snapshot());
                } else {
                    var settlement = group.settle(IDS.get(from), IDS.get(to), new Money(amount));
                    assertTrue(identifiers.add(settlement.id()));
                    entries++;
                    model[from] = model[from].add(BigInteger.valueOf(amount));
                    model[to] = model[to].subtract(BigInteger.valueOf(amount));
                }
            }
            var archive = new Archive(group.snapshot(), List.copyOf(Arrays.asList(model.clone())), entries);
            check(archive);
            archives.add(archive);
        }
        archives.forEach(GeneratedLedgerTest::check);
    }

    private static void check(Archive expected) {
        assertEquals(expected.entries(), expected.view().entries().size());
        for (int i = 0; i < 3; i++) {
            assertEquals(expected.balances().get(i), BigInteger.valueOf(expected.view().balanceOf(IDS.get(i)).grosz()));
        }
        assertEquals(BigInteger.ZERO, expected.view().balances().values().stream()
                .map(balance -> BigInteger.valueOf(balance.grosz())).reduce(BigInteger.ZERO, BigInteger::add));
    }
}

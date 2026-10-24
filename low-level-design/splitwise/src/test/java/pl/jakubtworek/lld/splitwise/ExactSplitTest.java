package pl.jakubtworek.lld.splitwise;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.splitwise.Fixtures.*;

/**
 * Kwoty wpisane ręcznie nie podlegają zaokrągleniu ani automatycznemu wyrównaniu.
 */
class ExactSplitTest {

    @Test
    void explicitAmountsBecomeCostsAndDefensiveCopiesProtectHistory() {
        var source = new HashMap<>(Map.of(U1, new Money(2000), U2, new Money(3500), U3, new Money(4500)));
        var policy = new ExactSplit(source);
        source.clear();
        var group = group();
        var expense = group.addExpense(U1, new Money(10000), policy, "Kolacja");
        assertEquals(Map.of(U1, new Balance(8000), U2, new Balance(-3500), U3, new Balance(-4500)), group.snapshot()
                .balances());
        assertEquals(new Money(2000), expense.shares().get(U1));
        assertThrows(UnsupportedOperationException.class, () -> policy.split(new Money(10000)).clear());
    }

    @Test
    void mismatchedTotalAndUnknownParticipantCannotPublish() {
        var group = group();
        var before = group.snapshot();
        assertThrows(IllegalArgumentException.class, () -> group.addExpense(U1, new Money(101),
                new ExactSplit(Map.of(U2, new Money(100))), "Błąd sumy"));
        assertEquals(SplitException.Reason.UNKNOWN_MEMBER, assertThrows(SplitException.class, () -> group.addExpense(U1,
                new Money(100), new ExactSplit(Map.of(new MemberId("X"), new Money(100))), "Obcy")).reason());
        assertSame(before, group.snapshot());
    }

    @Test
    void zeroSharesAndMaximumTotalAreRepresentableWithoutRedistribution() {
        assertEquals(Map.of(U1, new Money(0)), new ExactSplit(Map.of(U1, new Money(0))).split(new Money(0)));
        var shares = Map.of(U1, new Money(0), U2, new Money(Long.MAX_VALUE));
        assertEquals(shares, new ExactSplit(shares).split(new Money(Long.MAX_VALUE)));
        var group = group();
        group.addExpense(U1, new Money(Long.MAX_VALUE), new ExactSplit(shares), "Granica");
        assertEquals(new Balance(Long.MAX_VALUE), group.snapshot().balanceOf(U1));
        assertEquals(new Balance(-Long.MAX_VALUE), group.snapshot().balanceOf(U2));
    }

    @Test
    void invalidConfigurationAndOverflowFailBeforeAnyOperation() {
        assertThrows(IllegalArgumentException.class, () -> new ExactSplit(Map.of()));
        assertThrows(NullPointerException.class, () -> new ExactSplit(null));
        assertThrows(NullPointerException.class, () -> new ExactSplit(Map.of(U1, new Money(1))).split(null));
        assertThrows(ArithmeticException.class, () -> new ExactSplit(Map.of(U1, new Money(Long.MAX_VALUE), U2,
                new Money(1))));
    }
}

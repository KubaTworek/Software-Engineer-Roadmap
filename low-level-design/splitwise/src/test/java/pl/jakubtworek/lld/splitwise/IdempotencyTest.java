package pl.jakubtworek.lld.splitwise;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.splitwise.Fixtures.*;
import static pl.jakubtworek.lld.splitwise.SplitException.Reason.*;

/**
 * Retry porównujemy z zachowanym wynikiem i snapshotem, także po późniejszych operacjach.
 */
class IdempotencyTest {

    @Test
    void canonicalSharesAndTrimmedDescriptionIdentifySameExpenseAcrossStrategies() {
        var group = group();
        var before = group.snapshot();
        var key = new OperationKey(" receipt:1 ");
        var original = group.addExpense(key, U1, new Money(100), new EqualSplit(IDS), " Obiad ");
        var after = group.snapshot();
        assertSame(original, group.addExpense(new OperationKey("receipt:1"), U1, new Money(100),
                new ExactSplit(Map.of(U3, new Money(33), U2, new Money(33), U1, new Money(34))), "Obiad"));
        assertSame(after, group.snapshot());
        assertEquals(Map.of(key, original.id()), after.operationEntries());
        assertTrue(before.operationEntries().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> after.operationEntries().clear());
    }

    @Test
    void repeatedSettlementReturnsOriginalEvenAfterBalancesReachZero() {
        var group = group();
        group.addExpense(U1, new Money(100), new EqualSplit(List.of(U2)), "Obiad");
        var key = new OperationKey("transfer-1");
        var result = group.settle(key, U2, U1, new Money(100));
        var after = group.snapshot();
        assertSame(result, group.settle(key, U2, U1, new Money(100)));
        assertSame(after, group.snapshot());
        assertEquals(2, after.entries().size());
        assertTrue(after.balances().values().stream().allMatch(balance -> balance.grosz() == 0));
    }

    @Test
    void changedExpenseContentOrOperationKindConflictsWithoutMutation() {
        var group = group();
        var key = new OperationKey("purchase");
        group.addExpense(key, U1, new Money(100), new EqualSplit(IDS), "A");
        var before = group.snapshot();
        List<Runnable> conflicts = List.of(() -> group.addExpense(key, U2, new Money(100), new EqualSplit(IDS), "A"),
                () -> group.addExpense(key, U1, new Money(101), new EqualSplit(IDS), "A"), () -> group.addExpense(key,
                U1, new Money(100), new EqualSplit(List.of(U2)), "A"), () -> group.addExpense(key, U1, new Money(100),
                new EqualSplit(IDS), "B"), () -> group.settle(key, U2, U1, new Money(1)));
        for (var action : conflicts) {
            assertEquals(IDEMPOTENCY_CONFLICT, assertThrows(SplitException.class, action::run).reason());
            assertSame(before, group.snapshot());
        }
    }

    @Test
    void changedSettlementDataConflictsAndFailedRequestsDoNotReserveKeys() {
        var group = group();
        var key = new OperationKey("transfer");
        assertEquals(INVALID_SETTLEMENT_DIRECTION, assertThrows(SplitException.class, () -> group.settle(key, U2, U1,
                new Money(10))).reason());
        assertFalse(group.snapshot().operationEntries().containsKey(key));
        group.addExpense(U1, new Money(100), new EqualSplit(List.of(U2, U3)), "A");
        assertEquals(OVERPAYMENT, assertThrows(SplitException.class, () -> group.settle(key, U2, U1, new Money(51)))
                .reason());
        group.settle(key, U2, U1, new Money(10));
        var after = group.snapshot();
        for (var action : List.<Runnable>of(() -> group.settle(key, U2, U1, new Money(11)), () -> group.settle(key, U3,
                U1, new Money(10)), () -> group.settle(key, U2, U3, new Money(10)))) {
            assertEquals(IDEMPOTENCY_CONFLICT, assertThrows(SplitException.class, action::run).reason());
            assertSame(after, group.snapshot());
        }
    }

    @Test
    void policyFailureDoesNotConsumeKeyAndLegacyCallsRemainDistinctIntents() {
        var group = group();
        var key = new OperationKey("A");
        var before = group.snapshot();
        assertThrows(IllegalArgumentException.class, () -> group.addExpense(key, U1, new Money(100), total -> Map.of(),
                "A"));
        assertSame(before, group.snapshot());
        var first = group.addExpense(key, U1, new Money(100), new EqualSplit(IDS), "A");
        var other = group();
        var independent = other.addExpense(key, U2, new Money(50), new EqualSplit(IDS), "B");
        assertNotEquals(first.id(), independent.id());
        var legacy1 = group.addExpense(U1, new Money(100), new EqualSplit(IDS), "A");
        var legacy2 = group.addExpense(U1, new Money(100), new EqualSplit(IDS), "A");
        assertNotEquals(legacy1.id(), legacy2.id());
        assertEquals(3, group.snapshot().entries().size());
    }

    @Test
    void keyValidationPreservesCaseAndRejectsAmbiguousOrUnboundedInput() {
        assertEquals(new OperationKey("a"), new OperationKey(" a "));
        assertNotEquals(new OperationKey("a"), new OperationKey("A"));
        for (String value : List.of("", "a b", "x".repeat(65), "ą")) {
            assertThrows(IllegalArgumentException.class, () -> new OperationKey(value));
        }
        assertThrows(NullPointerException.class, () -> new OperationKey(null));
    }
}

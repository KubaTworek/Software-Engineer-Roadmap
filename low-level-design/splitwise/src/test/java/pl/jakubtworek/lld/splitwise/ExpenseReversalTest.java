package pl.jakubtworek.lld.splitwise;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.splitwise.Fixtures.*;
import static pl.jakubtworek.lld.splitwise.SplitException.Reason.*;

/**
 * Korekta jest przeciwnym zapisem kosztu; wykonane wcześniej transfery pozostają w historii.
 */
class ExpenseReversalTest {

    @Test
    void reversalKeepsOriginalAndRetryDoesNotReverseAgain() {
        var group = group();
        var expenseKey = new OperationKey("expense");
        var expense = group.addExpense(expenseKey, U1, new Money(100), new EqualSplit(IDS), "A");
        var old = group.snapshot();
        var key = new OperationKey("correction");
        var reversal = group.reverseExpense(key, U1, expense.id(), " Błędny rachunek ");
        var corrected = group.snapshot();
        assertEquals("Błędny rachunek", reversal.reason());
        assertSame(expense, corrected.entries().getFirst());
        assertSame(reversal, corrected.entries().getLast());
        assertTrue(corrected.balances().values().stream().allMatch(balance -> balance.grosz() == 0));
        assertEquals(new Balance(66), old.balanceOf(U1));
        assertSame(reversal, group.reverseExpense(key, U1, expense.id(), "Błędny rachunek"));
        // Retry dawnego wydatku zwraca dawny wynik, nie przywraca odwróconego kosztu.
        assertSame(expense, group.addExpense(expenseKey, U1, new Money(100), new EqualSplit(IDS), "A"));
        assertSame(corrected, group.snapshot());
        reject(group, ALREADY_REVERSED, () -> group.reverseExpense(new OperationKey("second"), U1, expense.id(),
                "Ponownie"));
        reject(group, IDEMPOTENCY_CONFLICT, () -> group.reverseExpense(key, U1, expense.id(), "Inny powód"));
        reject(group, IDEMPOTENCY_CONFLICT, () -> group.reverseExpense(key, U2, expense.id(), "Błędny rachunek"));
        reject(group, IDEMPOTENCY_CONFLICT, () -> group.reverseExpense(key, U1, reversal.id(), "Błędny rachunek"));
    }

    @Test
    void correctionAfterFullSettlementCreatesOppositeObligationAndCanBeSettled() {
        var group = group();
        var expense = group.addExpense(U1, new Money(100), new EqualSplit(List.of(U2)), "A");
        var transfer = group.settle(U2, U1, new Money(100));
        group.reverseExpense(new OperationKey("reverse"), U1, expense.id(), "Zwrot kosztu");
        assertSame(transfer, group.snapshot().entries().get(1));
        assertEquals(new Balance(-100), group.snapshot().balanceOf(U1));
        assertEquals(new Balance(100), group.snapshot().balanceOf(U2));
        group.settle(U1, U2, new Money(100));
        assertTrue(group.snapshot().balances().values().stream().allMatch(balance -> balance.grosz() == 0));
        assertEquals(4, group.snapshot().entries().size());
    }

    @Test
    void unknownActorWrongPayerAndNonExpenseTargetsCannotChangeJournal() {
        var group = group();
        var expense = group.addExpense(U1, new Money(100), new EqualSplit(List.of(U2)), "A");
        var transfer = group.settle(U2, U1, new Money(1));
        var key = new OperationKey("reverse");
        reject(group, UNKNOWN_MEMBER, () -> group.reverseExpense(key, new MemberId("X"), expense.id(), "A"));
        reject(group, UNKNOWN_ENTRY, () -> group.reverseExpense(key, U1, new EntryId(UUID.randomUUID()), "A"));
        reject(group, NOT_PAYER, () -> group.reverseExpense(key, U2, expense.id(), "A"));
        reject(group, NOT_EXPENSE, () -> group.reverseExpense(key, U1, transfer.id(), "A"));
        var correction = group.reverseExpense(key, U1, expense.id(), "A");
        reject(group, NOT_EXPENSE, () -> group.reverseExpense(new OperationKey("next"), U1, correction.id(), "A"));
    }

    @Test
    void overflowDuringReversalPreservesSnapshotAndKeyCanSucceedAfterSettlement() {
        var group = group();
        var expense = group.addExpense(U1, new Money(1), new EqualSplit(List.of(U2)), "A");
        group.addExpense(U2, new Money(Long.MAX_VALUE), new EqualSplit(List.of(U1)), "B");
        group.addExpense(U3, new Money(2), new EqualSplit(List.of(U1)), "C");
        assertEquals(new Balance(Long.MIN_VALUE), group.snapshot().balanceOf(U1));
        var key = new OperationKey("reverse");
        var before = group.snapshot();
        assertThrows(ArithmeticException.class, () -> group.reverseExpense(key, U1, expense.id(), "A"));
        assertSame(before, group.snapshot());
        assertFalse(before.operationEntries().containsKey(key));
        // Spłata przesuwa saldo U1 o grosz ku zeru i tworzy miejsce na korektę -1.
        group.settle(U1, U3, new Money(1));
        group.reverseExpense(key, U1, expense.id(), "A");
        assertEquals(new Balance(Long.MIN_VALUE), group.snapshot().balanceOf(U1));
        assertEquals(new Balance(Long.MAX_VALUE), group.snapshot().balanceOf(U2));
        assertEquals(new Balance(1), group.snapshot().balanceOf(U3));
        assertEquals(5, group.snapshot().entries().size());
    }

    @Test
    void ownCostReversalIsNetZeroAndInvalidReasonsDoNotConsumeKey() {
        var group = group();
        var expense = group.addExpense(U1, new Money(Long.MAX_VALUE), new EqualSplit(List.of(U1)), "Własny");
        var before = group.snapshot();
        var key = new OperationKey("reverse");
        assertThrows(IllegalArgumentException.class, () -> group.reverseExpense(key, U1, expense.id(), " "));
        assertThrows(NullPointerException.class, () -> group.reverseExpense(key, U1, expense.id(), null));
        assertSame(before, group.snapshot());
        group.reverseExpense(key, U1, expense.id(), "A");
        assertTrue(group.snapshot().balances().values().stream().allMatch(balance -> balance.grosz() == 0));
        assertEquals(expense.id(), EntryId.parse(" " + expense.id().value() + " "));
        assertThrows(IllegalArgumentException.class, () -> EntryId.parse("not-uuid"));
    }

    private static void reject(ExpenseGroup group, SplitException.Reason reason, Runnable action) {
        var before = group.snapshot();
        assertEquals(reason, assertThrows(SplitException.class, action::run).reason());
        assertSame(before, group.snapshot());
    }
}

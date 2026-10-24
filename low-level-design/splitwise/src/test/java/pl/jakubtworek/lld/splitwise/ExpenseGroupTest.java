package pl.jakubtworek.lld.splitwise;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.splitwise.Fixtures.*;
import static pl.jakubtworek.lld.splitwise.SplitException.Reason.*;

/**
 * Scenariusze sprawdzają zapis, salda i brak publikacji po błędzie, nie tylko wynik metody.
 */
class ExpenseGroupTest {

    @Test
    void expenseWithPayerShareCreatesExactlyBalancedProjection() {
        var group = group();
        var before = group.snapshot();
        var expense = group.addExpense(U1, new Money(100), new EqualSplit(IDS), " Obiad ");
        assertEquals(Map.of(U1, new Balance(66), U2, new Balance(-33), U3, new Balance(-33)), group.snapshot()
                .balances());
        assertEquals("Obiad", expense.description());
        assertSame(expense, group.snapshot().entries().getFirst());
        assertTrue(before.entries().isEmpty());
        assertEquals(new Balance(0), before.balanceOf(U1));
    }

    @Test
    void payerNeedNotParticipateInCostAndPartialSettlementsReduceBothSides() {
        var group = group();
        group.addExpense(U1, new Money(100), new EqualSplit(List.of(U2, U3)), "Prezent");
        var expenseView = group.snapshot();
        var settlement = group.settle(U2, U1, new Money(20));
        assertSame(settlement, group.snapshot().entries().get(1));
        assertEquals(new Balance(80), group.snapshot().balanceOf(U1));
        assertEquals(new Balance(-30), group.snapshot().balanceOf(U2));
        group.settle(U2, U1, new Money(30));
        group.settle(U3, U1, new Money(50));
        assertTrue(group.snapshot().balances().values().stream().allMatch(value -> value.grosz() == 0));
        assertEquals(new Balance(100), expenseView.balanceOf(U1));
        assertEquals(1, expenseView.entries().size());
    }

    @Test
    void netGroupSettlementCanBypassOriginalPairOfExpenseParticipants() {
        var group = group();
        group.addExpense(U1, new Money(100), new EqualSplit(List.of(U2)), "A za B");
        group.addExpense(U2, new Money(100), new EqualSplit(List.of(U3)), "B za C");
        assertEquals(new Balance(0), group.snapshot().balanceOf(U2));
        group.settle(U3, U1, new Money(100));
        assertTrue(group.snapshot().balances().values().stream().allMatch(value -> value.grosz() == 0));
    }

    @Test
    void overpaymentIsRejectedAgainstDebtorAndCreditorIndependently() {
        var group = group();
        group.addExpense(U1, new Money(100), new PercentageSplit(Map.of(U2, 4000, U3, 6000)), "Obiad");
        reject(group, OVERPAYMENT, () -> group.settle(U2, U1, new Money(41)));
        var other = group();
        other.addExpense(U1, new Money(40), new EqualSplit(List.of(U3)), "A");
        other.addExpense(U2, new Money(60), new EqualSplit(List.of(U3)), "B");
        reject(other, OVERPAYMENT, () -> other.settle(U3, U1, new Money(41)));
    }

    @Test
    void invalidDirectionSelfZeroAndUnknownMembersDoNotChangeState() {
        var group = group();
        reject(group, INVALID_SETTLEMENT_DIRECTION, () -> group.settle(U1, U2, new Money(1)));
        reject(group, SELF_SETTLEMENT, () -> group.settle(U1, U1, new Money(1)));
        reject(group, INVALID_AMOUNT, () -> group.settle(U1, U2, new Money(0)));
        reject(group, INVALID_AMOUNT, () -> group.addExpense(U1, new Money(0), new EqualSplit(IDS), "Zero"));
        reject(group, UNKNOWN_MEMBER, () -> group.addExpense(new MemberId("X"), new Money(1), new EqualSplit(IDS),
                "X"));
        reject(group, UNKNOWN_MEMBER, () -> group.addExpense(U1, new Money(1),
                new EqualSplit(List.of(new MemberId("X"))), "X"));
        group.addExpense(U1, new Money(100), new EqualSplit(List.of(U2)), "Obiad");
        reject(group, INVALID_SETTLEMENT_DIRECTION, () -> group.settle(U1, U2, new Money(1)));
        reject(group, INVALID_SETTLEMENT_DIRECTION, () -> group.settle(U2, U3, new Money(1)));
        reject(group, UNKNOWN_MEMBER, () -> group.settle(new MemberId("X"), U1, new Money(1)));
    }

    @Test
    void invalidOrFailingPolicyCannotPublishIncompleteExpense() {
        var group = group();
        List<SplitPolicy> policies = List.of(total -> Map.of(), total -> Map.of(U1, new Money(1)), total -> null,
                total -> {
            throw new IllegalStateException("Awaria strategii");
        });
        var before = group.snapshot();
        for (SplitPolicy policy : policies) {
            assertThrows(RuntimeException.class, () -> group.addExpense(U1, new Money(100), policy, "Obiad"));
            assertSame(before, group.snapshot());
        }
    }

    @Test
    void overflowKeepsJournalWhileNetZeroPayerChangeDoesNotFalselyOverflow() {
        var group = group();
        group.addExpense(U1, new Money(Long.MAX_VALUE), new EqualSplit(List.of(U2)), "Duży");
        var before = group.snapshot();
        assertThrows(ArithmeticException.class, () -> group.addExpense(U1, new Money(1), new EqualSplit(List.of(U2)),
                "Nadmiar"));
        assertSame(before, group.snapshot());
        group.addExpense(U1, new Money(1), new EqualSplit(List.of(U1)), "Własny");
        assertEquals(new Balance(Long.MAX_VALUE), group.snapshot().balanceOf(U1));
        assertEquals(2, group.snapshot().entries().size());
    }

    @Test
    void minimumSignedBalanceCanBeSettledWithoutAbsoluteValueOverflow() {
        var group = group();
        group.addExpense(U1, new Money(Long.MAX_VALUE), new EqualSplit(List.of(U3)), "A");
        group.addExpense(U2, new Money(1), new EqualSplit(List.of(U3)), "B");
        assertEquals(new Balance(Long.MIN_VALUE), group.snapshot().balanceOf(U3));
        var before = group.snapshot();
        assertThrows(ArithmeticException.class, () -> group.addExpense(U2, new Money(1), new EqualSplit(List.of(U3)),
                "Nadmiar długu"));
        assertSame(before, group.snapshot());
        group.settle(U3, U2, new Money(1));
        group.settle(U3, U1, new Money(Long.MAX_VALUE));
        assertTrue(group.snapshot().balances().values().stream().allMatch(value -> value.grosz() == 0));
    }

    @Test
    void mutableSourcesCannotChangeMembersHistoryOrRecordedShares() {
        var members = new ArrayList<>(List.of(new Member(U1, "A"), new Member(U2, "B")));
        var group = new ExpenseGroup(members);
        members.clear();
        var shares = new HashMap<>(Map.of(U1, new Money(50), U2, new Money(50)));
        var expense = group.addExpense(U1, new Money(100), total -> shares, "Obiad");
        shares.clear();
        var view = group.snapshot();
        assertEquals(2, expense.shares().size());
        assertThrows(UnsupportedOperationException.class, () -> view.members().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.balances().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.entries().clear());
        assertThrows(UnsupportedOperationException.class, () -> expense.shares().clear());
    }

    @Test
    void nullArgumentsAndInvalidGroupConfigurationAreProgrammingErrors() {
        assertThrows(IllegalArgumentException.class, () -> new ExpenseGroup(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ExpenseGroup(List.of(new Member(U1, "A"),
                new Member(new MemberId(" u1 "), "B"))));
        var group = group();
        var before = group.snapshot();
        List<Runnable> actions = List.of(() -> group.addExpense(null, new Money(1), new EqualSplit(IDS), "A"),
                () -> group.addExpense(U1, null, new EqualSplit(IDS), "A"), () -> group.addExpense(U1, new Money(1),
                null, "A"), () -> group.addExpense(U1, new Money(1), new EqualSplit(IDS), null),
                () -> group.settle(null, U2, new Money(1)), () -> group.settle(U1, null, new Money(1)),
                () -> group.settle(U1, U2, null));
        for (Runnable action : actions) {
            assertThrows(NullPointerException.class, action::run);
            assertSame(before, group.snapshot());
        }
    }

    private static void reject(ExpenseGroup group, SplitException.Reason reason, Runnable action) {
        var before = group.snapshot();
        assertEquals(reason, assertThrows(SplitException.class, action::run).reason());
        assertSame(before, group.snapshot());
    }
}

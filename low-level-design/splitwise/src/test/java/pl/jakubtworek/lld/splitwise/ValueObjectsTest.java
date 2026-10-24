package pl.jakubtworek.lld.splitwise;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.splitwise.Fixtures.*;

/**
 * Kwota i saldo mają różne zakresy znaczeniowe mimo wspólnej jednostki groszy.
 */
class ValueObjectsTest {

    @ParameterizedTest
    @ValueSource(strings = { "", " ", "A B", "A/B", "ą", "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567" })
    void invalidCodesCannotBecomeMemberIdentity(String code) {
        assertThrows(IllegalArgumentException.class, () -> new MemberId(code));
    }

    @Test
    void moneyFormattingAndSignedBalancesHaveExactDecimalRepresentation() {
        assertEquals(U1, new MemberId(" u1 "));
        assertEquals("123.45 PLN", new Money(12345).toString());
        assertEquals("+123.45 PLN", new Balance(12345).toString());
        assertEquals("-92233720368547758.08 PLN", new Balance(Long.MIN_VALUE).toString());
        assertThrows(IllegalArgumentException.class, () -> new Money(-1));
        assertThrows(ArithmeticException.class, () -> new Money(Long.MAX_VALUE).plus(new Money(1)));
    }

    @Test
    void invalidValuesAndMalformedEntriesAreRejected() {
        var id = new EntryId(UUID.randomUUID());
        assertThrows(NullPointerException.class, () -> new MemberId(null));
        assertThrows(NullPointerException.class, () -> new EntryId(null));
        assertThrows(IllegalArgumentException.class, () -> new Member(U1, " "));
        assertThrows(IllegalArgumentException.class, () -> new Expense(id, U1, new Money(100), Map.of(U2,
                new Money(99)), "A"));
        assertThrows(IllegalArgumentException.class, () -> new Settlement(id, U1, U1, new Money(1)));
        assertThrows(IllegalArgumentException.class, () -> new Settlement(id, U1, U2, new Money(0)));
        var single = new ExpenseGroup(List.of(new Member(U1, "A")));
        single.addExpense(U1, new Money(1), new EqualSplit(List.of(U1)), "Własny");
        assertEquals(new Balance(0), single.snapshot().balanceOf(U1));
        assertEquals(SplitException.Reason.UNKNOWN_MEMBER, assertThrows(SplitException.class, () -> single.snapshot()
                .balanceOf(U2)).reason());
    }
}

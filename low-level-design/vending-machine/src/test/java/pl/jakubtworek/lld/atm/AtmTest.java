package pl.jakubtworek.lld.atm;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.atm.Fixtures.*;
import static pl.jakubtworek.lld.atm.AtmException.Reason.*;

/**
 * Operacje sprawdzamy razem z kontem, kasetą, historią i zachowanymi widokami.
 */
class AtmTest {

    @Test
    void loginWithdrawAndEjectPublishConsistentMoneyAndHistory() {
        var atm = atm();
        var initial = atm.snapshot();
        login(atm);
        var before = atm.snapshot();
        var receipt = atm.withdraw(sid(atm), new Money(6000));
        assertEquals(new Money(94000), atm.balance(sid(atm)));
        assertEquals(new Money(54000), atm.snapshot().cash().total());
        assertEquals(new Money(6000), receipt.cash().total());
        assertEquals(new Money(94000), receipt.balanceAfter());
        assertSame(receipt, atm.history(sid(atm)).getFirst());
        assertEquals(C1, atm.ejectCard(sid(atm)));
        assertInstanceOf(Session.Idle.class, atm.snapshot().session());
        assertEquals(new Money(100000), before.accounts().get(A1).balance());
        assertTrue(initial.withdrawals().isEmpty());
        assertEquals(new Money(60000), initial.cash().total());
    }

    @Test
    void threeBadPinsEjectCardAndReinsertionStartsFreshSession() {
        var atm = atm();
        atm.insertCard(C1);
        assertEquals(AuthenticationResult.REJECTED, atm.enterPin(sid(atm), Pin.parse("0000")));
        assertEquals(new Session.CardInserted(sid(atm), C1, 1, NOW.plus(TTL)), atm.snapshot().session());
        assertEquals(AuthenticationResult.REJECTED, atm.enterPin(sid(atm), Pin.parse("0000")));
        assertEquals(AuthenticationResult.CARD_EJECTED, atm.enterPin(sid(atm), Pin.parse("0000")));
        assertInstanceOf(Session.Idle.class, atm.snapshot().session());
        login(atm);
        assertEquals(new Money(100000), atm.balance(sid(atm)));
    }

    @Test
    void correctPinAfterTwoMistakesAuthenticatesAndCannotBeSubmittedAgain() {
        var atm = atm();
        atm.insertCard(C1);
        atm.enterPin(sid(atm), Pin.parse("0000"));
        atm.enterPin(sid(atm), Pin.parse("0000"));
        assertEquals(AuthenticationResult.AUTHENTICATED, atm.enterPin(sid(atm), Pin.parse("1234")));
        reject(atm, INVALID_SESSION_STATE, () -> atm.enterPin(sid(atm), Pin.parse("1234")));
    }

    @Test
    void authFailureDoesNotConsumeAttemptOrPublishAnything() {
        var atm = atm(cash(), (card, pin) -> {
            throw new IllegalStateException("Awaria weryfikacji");
        });
        atm.insertCard(C1);
        var before = atm.snapshot();
        assertThrows(IllegalStateException.class, () -> atm.enterPin(sid(atm), Pin.parse("1234")));
        assertSame(before, atm.snapshot());
    }

    @Test
    void invalidSessionTransitionsAndUnknownCardsRejectWithoutChange() {
        var atm = atm();
        reject(atm, INVALID_SESSION_STATE, () -> atm.balance(sid(atm)));
        reject(atm, INVALID_SESSION_STATE, () -> atm.history(sid(atm)));
        reject(atm, INVALID_SESSION_STATE, () -> atm.withdraw(sid(atm), new Money(1000)));
        reject(atm, INVALID_SESSION_STATE, () -> atm.enterPin(sid(atm), Pin.parse("1234")));
        reject(atm, INVALID_SESSION_STATE, () -> atm.ejectCard(sid(atm)));
        reject(atm, UNKNOWN_CARD, () -> atm.insertCard(new CardId("X")));
        atm.insertCard(C1);
        reject(atm, INVALID_SESSION_STATE, () -> atm.insertCard(C2));
        reject(atm, INVALID_SESSION_STATE, () -> atm.withdraw(sid(atm), new Money(1000)));
        atm.ejectCard(sid(atm));
        assertInstanceOf(Session.Idle.class, atm.snapshot().session());
    }

    @Test
    void moneyAmountBalanceCashAndExactCompositionHaveSeparateRefusals() {
        var atm = atm();
        login(atm);
        reject(atm, INVALID_AMOUNT, () -> atm.withdraw(sid(atm), new Money(0)));
        reject(atm, INVALID_AMOUNT, () -> atm.withdraw(sid(atm), new Money(1001)));
        reject(atm, AMOUNT_LIMIT_EXCEEDED, () -> atm.withdraw(sid(atm), new Money(201000)));
        reject(atm, INSUFFICIENT_FUNDS, () -> atm.withdraw(sid(atm), new Money(101000)));
        reject(atm, INSUFFICIENT_CASH, () -> atm.withdraw(sid(atm), new Money(61000)));
        var impossible = atm(new CashInventory(Map.of(Banknote.PLN_50, 2)), auth());
        login(impossible);
        reject(impossible, CANNOT_DISPENSE, () -> impossible.withdraw(sid(impossible), new Money(6000)));
    }

    @Test
    void limitedBanknotesCanBeWithdrawnAndNextAttemptKeepsState() {
        var atm = atm(new CashInventory(Map.of(Banknote.PLN_50, 1, Banknote.PLN_20, 3)), auth());
        login(atm);
        assertEquals(Map.of(Banknote.PLN_20, 3), atm.withdraw(sid(atm), new Money(6000)).cash().notes());
        reject(atm, INSUFFICIENT_CASH, () -> atm.withdraw(sid(atm), new Money(6000)));
        assertEquals(1, atm.snapshot().withdrawals().size());
        assertEquals(new Money(94000), atm.balance(sid(atm)));
    }

    @Test
    void accountHistoryIsFilteredAndSharedCardsSeeSameAccount() {
        var atm = new Atm(List.of(new Account(A1, new Money(100000))), Map.of(C1, A1, C2, A1), cash(), auth(), CLOCK,
                TTL);
        login(atm);
        atm.withdraw(sid(atm), new Money(1000));
        atm.ejectCard(sid(atm));
        atm.insertCard(C2);
        atm.enterPin(sid(atm), Pin.parse("4321"));
        assertEquals(new Money(99000), atm.balance(sid(atm)));
        assertEquals(1, atm.history(sid(atm)).size());
        var independent = atm();
        login(independent);
        independent.withdraw(sid(independent), new Money(1000));
        independent.ejectCard(sid(independent));
        independent.insertCard(C2);
        independent.enterPin(sid(independent), Pin.parse("4321"));
        assertTrue(independent.history(sid(independent)).isEmpty());
        assertEquals(new Money(40000), independent.balance(sid(independent)));
    }

    @Test
    void configurationInputAndOldViewsCannotBeMutated() {
        var accounts = new ArrayList<>(List.of(new Account(A1, new Money(100000))));
        var cards = new HashMap<>(Map.of(C1, A1));
        var atm = new Atm(accounts, cards, cash(), auth(), CLOCK, TTL);
        accounts.clear();
        cards.clear();
        login(atm);
        atm.withdraw(sid(atm), new Money(1000));
        assertThrows(UnsupportedOperationException.class, () -> atm.snapshot().accounts().clear());
        assertThrows(UnsupportedOperationException.class, () -> atm.snapshot().withdrawals().clear());
        assertThrows(UnsupportedOperationException.class, () -> atm.snapshot().cash().notes().clear());
        assertThrows(UnsupportedOperationException.class, () -> atm.history(sid(atm)).clear());
        assertThrows(UnsupportedOperationException.class, () -> atm.history(sid(atm)).getFirst().cash().notes()
                .clear());
    }

    @Test
    void invalidConfigurationAndNullArgumentsAreProgrammingErrors() {
        assertThrows(IllegalArgumentException.class, () -> new Atm(List.of(), Map.of(C1, A1), cash(), auth()));
        assertThrows(IllegalArgumentException.class, () -> new Atm(List.of(new Account(A1, new Money(1))), Map.of(C1,
                A2), cash(), auth()));
        var account = new Account(A1, new Money(1));
        assertThrows(IllegalArgumentException.class, () -> new Atm(List.of(account, account), Map.of(C1, A1), cash(),
                auth()));
        assertThrows(NullPointerException.class, () -> new Atm(List.of(account), Map.of(C1, A1), cash(), null));
        var atm = atm();
        var before = atm.snapshot();
        assertThrows(NullPointerException.class, () -> atm.insertCard(null));
        assertThrows(NullPointerException.class, () -> atm.enterPin(sid(atm), null));
        assertThrows(NullPointerException.class, () -> atm.withdraw(sid(atm), null));
        assertSame(before, atm.snapshot());
    }

    private static void reject(Atm atm, AtmException.Reason reason, Runnable action) {
        var before = atm.snapshot();
        assertEquals(reason, assertThrows(AtmException.class, action::run).reason());
        assertSame(before, atm.snapshot());
    }
}

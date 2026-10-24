package pl.jakubtworek.lld.atm;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.atm.Fixtures.*;
import static pl.jakubtworek.lld.atm.AtmException.Reason.*;

/**
 * 400 rund łączy nowe sesje, stare tokeny, retry, konflikty i czas. Kaseta
 * ma tylko 10 PLN, więc niezależny model zna dokładny ubytek sztuk bez DP.
 * Ustalona próbka uzupełnia generator 1000 dawnych komend, nie zastępuje go.
 */
class GeneratedSessionRetryTest {

    private record OldView(AtmSnapshot view, Session session, Map<AccountId, Account> accounts, Map<WithdrawalKey,
            WithdrawalReceipt> operations) {
    }

    @ParameterizedTest
    @ValueSource(longs = { 17, 89, 2026, 104729 })
    void generatedSessionSwitchesRetryAndExpirationPreserveIndependentMoney(long seed) {
        var clock = new MutableClock();
        var random = new Random(seed);
        var ttl = Duration.ofSeconds(10);
        var atm = new Atm(List.of(new Account(A1, new Money(1000000)), new Account(A2, new Money(1000000))), Map.of(C1,
                A1, C2, A2), new CashInventory(Map.of(Banknote.PLN_10, 2000)), auth(), clock, ttl);
        var model = new HashMap<WithdrawalKey, WithdrawalReceipt>();
        var history = new ArrayList<WithdrawalReceipt>();
        var balances = new HashMap<>(Map.of(A1, BigInteger.valueOf(1000000), A2, BigInteger.valueOf(1000000)));
        var views = new ArrayList<OldView>();
        SessionId previous = null;
        for (int round = 0; round < 100; round++) {
            var card = round == 0 || random.nextBoolean() ? C1 : C2;
            var account = card.equals(C1) ? A1 : A2;
            var started = clock.instant();
            var id = atm.insertCard(card);
            var before = atm.snapshot();
            views.add(new OldView(before, before.session(), Map.copyOf(before.accounts()),
                    Map.copyOf(before.operations())));
            if (previous != null) {
                var old = previous;
                List<Runnable> stale = List.of(() -> atm.enterPin(old, Pin.parse("1234")), () -> atm.ejectCard(old),
                        () -> atm.balance(old), () -> atm.history(old), () -> atm.withdraw(old, new OperationKey("k0"),
                        new Money(1000)));
                assertEquals(STALE_SESSION, assertThrows(AtmException.class, stale.get(round % stale.size())::run)
                        .reason());
                assertSame(before, atm.snapshot());
            }
            atm.enterPin(id, Pin.parse(card.equals(C1) ? "1234" : "4321"));
            var key = new OperationKey("k" + (round == 0 ? 0 : random.nextInt(10)));
            var scoped = new WithdrawalKey(account, key);
            var amount = new Money(round == 0 || random.nextBoolean() ? 1000 : 2000);
            int offset = round == 0 ? 0 : random.nextInt(11);
            clock.at(started.plusSeconds(offset));
            before = atm.snapshot();
            if (offset == 10) {
                assertEquals(SESSION_EXPIRED, assertThrows(AtmException.class, () -> atm.withdraw(id, key, amount))
                        .reason());
                assertInstanceOf(Session.Idle.class, atm.snapshot().session());
            } else {
                var existing = model.get(scoped);
                if (existing != null && !existing.amount().equals(amount)) {
                    assertEquals(IDEMPOTENCY_CONFLICT, assertThrows(AtmException.class, () -> atm.withdraw(id, key,
                            amount)).reason());
                    assertSame(before, atm.snapshot());
                } else {
                    var receipt = atm.withdraw(id, key, amount);
                    if (existing != null) {
                        assertSame(existing, receipt);
                        assertSame(before, atm.snapshot());
                    } else {
                        balances.put(account, balances.get(account).subtract(BigInteger.valueOf(amount.grosz())));
                        assertEquals(balances.get(account).longValueExact(), receipt.balanceAfter().grosz());
                        assertEquals(Map.of(Banknote.PLN_10, (int) (amount.grosz() / 1000)), receipt.cash().notes());
                        model.put(scoped, receipt);
                        history.add(receipt);
                    }
                }
                if (round % 3 == 0) {
                    clock.at(started.plus(ttl));
                    assertEquals(SESSION_EXPIRED, assertThrows(AtmException.class, () -> atm.withdraw(id, key, amount))
                            .reason());
                } else {
                    atm.ejectCard(id);
                }
            }
            assertInstanceOf(Session.Idle.class, atm.snapshot().session());
            assertEquals(model, atm.snapshot().operations());
            assertEquals(history, atm.snapshot().withdrawals());
            balances.forEach((acc, balance) -> assertEquals(balance.longValueExact(), atm.snapshot().accounts().get(acc)
                    .balance().grosz()));
            var paid = history.stream().map(receipt -> BigInteger.valueOf(receipt.amount().grosz()))
                    .reduce(BigInteger.ZERO, BigInteger::add);
            assertEquals(2000 - paid.divide(BigInteger.valueOf(1000)).intValueExact(), atm.snapshot().cash()
                    .count(Banknote.PLN_10));
            previous = id;
            clock.at(clock.instant().plusSeconds(1));
        }
        assertFalse(model.isEmpty());
        for (var old : views) {
            assertEquals(old.session(), old.view().session());
            assertEquals(old.accounts(), old.view().accounts());
            assertEquals(old.operations(), old.view().operations());
        }
    }
}

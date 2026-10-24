package pl.jakubtworek.lld.atm;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.atm.Fixtures.*;
import static pl.jakubtworek.lld.atm.AtmException.Reason.*;

/**
 * 1000 komend z ustalonymi ziarnami. Salda i zachowanie kwoty liczymy niezależnie
 * w BigInteger. Wykonalność wypłaty sprawdza enumeracja ograniczonych liczności,
 * a nie produkcyjne DP. To próbka historii, nie dowód wszystkich sesji i kwot.
 */
class GeneratedAtmTest {

    private record OldView(AtmSnapshot view, Map<AccountId, Account> accounts, Map<Banknote, Integer> cash,
            Session session, List<WithdrawalReceipt> receipts) {
    }

    @ParameterizedTest
    @ValueSource(longs = { 13, 71, 2026, 65537 })
    void generatedSessionsPreserveIndependentBalancesCashAndOldViews(long seed) {
        var random = new Random(seed);
        var atm = atm();
        var balances = new HashMap<>(Map.of(A1, BigInteger.valueOf(100000), A2, BigInteger.valueOf(40000)));
        var notes = new EnumMap<Banknote, Integer>(Banknote.class);
        notes.putAll(atm.snapshot().cash().notes());
        var receipts = new ArrayList<WithdrawalReceipt>();
        var views = new ArrayList<OldView>();
        Session session = new Session.Idle();
        for (int step = 0; step < 250; step++) {
            var before = atm.snapshot();
            views.add(new OldView(before, Map.copyOf(before.accounts()), Map.copyOf(before.cash().notes()),
                    before.session(), List.copyOf(before.withdrawals())));
            // Pierwsze trzy komendy zapewniają co najmniej jedną udaną wypłatę
            // w każdej historii. Pozostałe operacje są próbką losową ze stałym ziarnem.
            int action = step < 3 ? step : random.nextInt(6);
            switch(action) {
                case 0 ->
                    {
                        var card = step == 0 ? C1 : List.of(C1, C2, new CardId("X")).get(random.nextInt(3));
                        var reason = !(session instanceof Session.Idle) ? INVALID_SESSION_STATE : card.value()
                                .equals("X") ? UNKNOWN_CARD : null;
                        if (reason != null) {
                            reject(atm, reason, () -> atm.insertCard(card));
                        } else {
                            var id = atm.insertCard(card);
                            session = new Session.CardInserted(id, card, 0, NOW.plus(TTL));
                        }
                    }
                case 1 ->
                    {
                        String code = step == 1 ? "1234" : List.of("1234", "4321", "0000").get(random.nextInt(3));
                        if (!(session instanceof Session.CardInserted inserted)) {
                            reject(atm, INVALID_SESSION_STATE, () -> atm.enterPin(sid(atm), Pin.parse(code)));
                        } else {
                            boolean correct = inserted.card().equals(C1) ? code.equals("1234") : code.equals("4321");
                            var expected = correct ? AuthenticationResult.AUTHENTICATED : inserted.failedAttempts() == 2 ? AuthenticationResult.CARD_EJECTED : AuthenticationResult.REJECTED;
                            assertEquals(expected, atm.enterPin(sid(atm), Pin.parse(code)));
                            session = correct ? new Session.Authenticated(inserted.id(), inserted.card(),
                                    inserted.card().equals(C1) ? A1 : A2,
                                    inserted.expiresAt()) : inserted.failedAttempts() == 2 ? new Session.Idle() : new Session.CardInserted(inserted.id(), inserted.card(), inserted.failedAttempts() + 1, inserted.expiresAt());
                        }
                    }
                case 2 ->
                    {
                        var amount = new Money(step == 2 ? 1000 : random.nextInt(250) * 1000L + (random.nextInt(10) == 0 ? 1 : 0));
                        AtmException.Reason reason;
                        if (!(session instanceof Session.Authenticated)) {
                            reason = INVALID_SESSION_STATE;
                        } else if (amount.grosz() == 0 || amount.grosz() % 1000 != 0) {
                            reason = INVALID_AMOUNT;
                        } else if (amount.grosz() > 200000) {
                            reason = AMOUNT_LIMIT_EXCEEDED;
                        } else {
                            var current = (Session.Authenticated) session;
                            reason = balances.get(current.account())
                                    .compareTo(BigInteger.valueOf(amount.grosz())) < 0 ? INSUFFICIENT_FUNDS : total(notes).compareTo(BigInteger.valueOf(amount.grosz())) < 0 ? INSUFFICIENT_CASH : !possible(notes, Banknote.values(), 0, amount.grosz()) ? CANNOT_DISPENSE : null;
                        }
                        if (reason != null) {
                            reject(atm, reason, () -> atm.withdraw(sid(atm), amount));
                        } else {
                            var receipt = atm.withdraw(sid(atm), amount);
                            var account = ((Session.Authenticated) session).account();
                            assertEquals(account, receipt.account());
                            assertEquals(amount, receipt.amount());
                            assertEquals(amount, receipt.cash().total());
                            receipt.cash().notes().forEach((note, count) -> {
                                assertTrue(count <= notes.getOrDefault(note, 0));
                                notes.put(note, notes.getOrDefault(note, 0) - count);
                            });
                            balances.put(account, balances.get(account).subtract(BigInteger.valueOf(amount.grosz())));
                            assertEquals(balances.get(account).longValueExact(), receipt.balanceAfter().grosz());
                            receipts.add(receipt);
                        }
                    }
                case 3 ->
                    {
                        if (session instanceof Session.Authenticated current) {
                            assertEquals(balances.get(current.account()).longValueExact(), atm.balance(sid(atm))
                                    .grosz());
                        } else {
                            reject(atm, INVALID_SESSION_STATE, () -> atm.balance(sid(atm)));
                        }
                    }
                case 4 ->
                    {
                        if (session instanceof Session.Idle) {
                            reject(atm, INVALID_SESSION_STATE, () -> atm.ejectCard(sid(atm)));
                        } else {
                            atm.ejectCard(sid(atm));
                            session = new Session.Idle();
                        }
                    }
                case 5 ->
                    {
                        if (session instanceof Session.Authenticated current) {
                            assertEquals(receipts.stream().filter(receipt -> receipt.account()
                                    .equals(current.account())).toList(), atm.history(sid(atm)));
                        } else {
                            reject(atm, INVALID_SESSION_STATE, () -> atm.history(sid(atm)));
                        }
                    }
                default ->
                    throw new AssertionError();
            }
            assertEquals(session, atm.snapshot().session());
            balances.forEach((id, balance) -> assertEquals(balance.longValueExact(), atm.snapshot().accounts().get(id)
                    .balance().grosz()));
            for (var note : Banknote.values()) {
                assertEquals(notes.getOrDefault(note, 0).intValue(), atm.snapshot().cash().count(note));
            }
            assertEquals(receipts, atm.snapshot().withdrawals());
            assertEquals(receipts.size(), new HashSet<>(receipts.stream().map(WithdrawalReceipt::id).toList()).size());
            var paid = receipts.stream().map(receipt -> BigInteger.valueOf(receipt.amount().grosz()))
                    .reduce(BigInteger.ZERO, BigInteger::add);
            assertEquals(BigInteger.valueOf(60000), total(notes).add(paid));
            assertEquals(BigInteger.valueOf(140000), balances.values().stream().reduce(BigInteger.ZERO, BigInteger::add)
                    .add(paid));
        }
        assertFalse(receipts.isEmpty());
        for (var old : views) {
            assertEquals(old.accounts(), old.view().accounts());
            assertEquals(old.cash(), old.view().cash().notes());
            assertEquals(old.session(), old.view().session());
            assertEquals(old.receipts(), old.view().withdrawals());
        }
    }

    /**
     * Pełna enumeracja liczności pięciu nominałów, z odcięciem gałęzi przekraczających kwotę.
     */
    private static boolean possible(Map<Banknote, Integer> notes, Banknote[] kinds, int index, long remaining) {
        if (remaining == 0) {
            return true;
        }
        if (index == kinds.length) {
            return false;
        }
        var note = kinds[index];
        int max = (int) Math.min(notes.getOrDefault(note, 0), remaining / note.grosz());
        for (int count = 0; count <= max; count++) {
            if (possible(notes, kinds, index + 1, remaining - (long) count * note.grosz())) {
                return true;
            }
        }
        return false;
    }

    private static BigInteger total(Map<Banknote, Integer> notes) {
        return notes.entrySet().stream().map(item -> BigInteger.valueOf(item.getKey().grosz())
                .multiply(BigInteger.valueOf(item.getValue()))).reduce(BigInteger.ZERO, BigInteger::add);
    }

    private static void reject(Atm atm, AtmException.Reason reason, Runnable action) {
        var before = atm.snapshot();
        assertEquals(reason, assertThrows(AtmException.class, action::run).reason());
        assertSame(before, atm.snapshot());
    }
}

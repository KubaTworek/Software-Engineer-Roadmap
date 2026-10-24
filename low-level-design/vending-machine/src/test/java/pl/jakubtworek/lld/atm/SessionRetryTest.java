package pl.jakubtworek.lld.atm;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.atm.Fixtures.*;
import static pl.jakubtworek.lld.atm.AtmException.Reason.*;

/**
 * Testy wiążą tożsamość, prawo dostępu, czas i intencję. Nie pobierają ID na nowo przed starym poleceniem.
 */
class SessionRetryTest {

    @Test
    void staleCommandsCannotReadAuthenticateWithdrawOrEjectNewSessionEvenAfterItsDeadline() {
        var clock = new MutableClock();
        var atm = timed(clock);
        var old = atm.insertCard(C1);
        atm.enterPin(old, Pin.parse("1234"));
        var key = new OperationKey("receipt-1");
        atm.withdraw(old, key, new Money(1000));
        atm.ejectCard(old);
        var current = atm.insertCard(C2);
        assertNotEquals(old, current);
        clock.at(NOW.plus(TTL));
        var before = atm.snapshot();
        List<Runnable> stale = List.of(() -> atm.enterPin(old, Pin.parse("1234")), () -> atm.balance(old),
                () -> atm.history(old), () -> atm.withdraw(old, key, new Money(1000)), () -> atm.ejectCard(old));
        for (var command : stale) {
            reject(atm, STALE_SESSION, command);
        }
        assertSame(before, atm.snapshot());
        // Dopiero polecenie aktualnego właściciela rozpoznaje wygaśnięcie.
        assertEquals(SESSION_EXPIRED, assertThrows(AtmException.class, () -> atm.enterPin(current, Pin.parse("4321")))
                .reason());
        assertInstanceOf(Session.Idle.class, atm.snapshot().session());
    }

    @Test
    void sameCardReinsertedGetsNewIdAndAuthenticationDoesNotChangeItOrDeadline() {
        var clock = new MutableClock();
        var atm = timed(clock);
        var first = atm.insertCard(C1);
        atm.ejectCard(first);
        var second = atm.insertCard(C1);
        clock.at(NOW.plusSeconds(100));
        atm.enterPin(second, Pin.parse("1234"));
        var session = assertInstanceOf(Session.Authenticated.class, atm.snapshot().session());
        assertNotEquals(first, second);
        assertEquals(second, session.id());
        assertEquals(NOW.plus(TTL), session.expiresAt());
    }

    @Test
    void expirationBoundaryBeforePinDoesNotInvokeAuthenticatorOrConsumeAnotherAttempt() {
        var calls = new AtomicInteger();
        var clock = new MutableClock();
        var atm = new Atm(List.of(new Account(A1, new Money(10000))), Map.of(C1, A1), cash(), (card, pin) -> {
            calls.incrementAndGet();
            return false;
        }, clock, TTL);
        var id = atm.insertCard(C1);
        var original = atm.snapshot();
        clock.at(NOW.plus(TTL).minusNanos(1));
        assertEquals(AuthenticationResult.REJECTED, atm.enterPin(id, Pin.parse("0000")));
        var beforeExpiry = atm.snapshot();
        assertEquals(1, calls.get());
        clock.at(NOW.plus(TTL));
        assertEquals(SESSION_EXPIRED, assertThrows(AtmException.class, () -> atm.enterPin(id, Pin.parse("0000")))
                .reason());
        assertEquals(1, calls.get());
        assertInstanceOf(Session.Idle.class, atm.snapshot().session());
        assertEquals(original.accounts(), atm.snapshot().accounts());
        assertSame(beforeExpiry.cash(), atm.snapshot().cash());
        assertEquals(0, ((Session.CardInserted) original.session()).failedAttempts());
    }

    @Test
    void retryReturnsSameReceiptWithEmptyCashAndZeroBalanceWithoutRepublishing() {
        var atm = new Atm(List.of(new Account(A1, new Money(1000))), Map.of(C1, A1),
                new CashInventory(Map.of(Banknote.PLN_10, 1)), auth(), CLOCK, TTL);
        var id = atm.insertCard(C1);
        atm.enterPin(id, Pin.parse("1234"));
        var key = new OperationKey("one");
        var receipt = atm.withdraw(id, key, new Money(1000));
        var after = atm.snapshot();
        assertSame(receipt, atm.withdraw(id, key, new Money(1000)));
        assertSame(after, atm.snapshot());
        assertEquals(new Money(0), atm.balance(id));
        assertTrue(after.cash().notes().isEmpty());
        reject(atm, IDEMPOTENCY_CONFLICT, () -> atm.withdraw(id, key, new Money(2000)));
        reject(atm, IDEMPOTENCY_CONFLICT, () -> atm.withdraw(id, key, new Money(0)));
    }

    @Test
    void retryAfterDeadlineEndsSessionButKeepsAllCommittedMoneyAndKeys() {
        var clock = new MutableClock();
        var atm = timed(clock);
        var id = atm.insertCard(C1);
        atm.enterPin(id, Pin.parse("1234"));
        var key = new OperationKey("one");
        var receipt = atm.withdraw(id, key, new Money(1000));
        var after = atm.snapshot();
        clock.at(NOW.plus(TTL).minusNanos(1));
        assertSame(receipt, atm.withdraw(id, key, new Money(1000)));
        assertSame(after, atm.snapshot());
        clock.at(NOW.plus(TTL));
        assertEquals(SESSION_EXPIRED, assertThrows(AtmException.class, () -> atm.withdraw(id, key, new Money(1000)))
                .reason());
        assertInstanceOf(Session.Idle.class, atm.snapshot().session());
        assertEquals(after.accounts(), atm.snapshot().accounts());
        assertSame(after.cash(), atm.snapshot().cash());
        assertEquals(after.withdrawals(), atm.snapshot().withdrawals());
        assertEquals(after.operations(), atm.snapshot().operations());
        assertInstanceOf(Session.Authenticated.class, after.session());
    }

    @Test
    void reauthenticationAllowsRetryAcrossSessionsAndCardsOfSameAccountButNeverWithoutAuthentication() {
        var atm = new Atm(List.of(new Account(A1, new Money(100000))), Map.of(C1, A1, C2, A1), cash(), auth(), CLOCK,
                TTL);
        var first = atm.insertCard(C1);
        atm.enterPin(first, Pin.parse("1234"));
        var key = new OperationKey("one");
        var receipt = atm.withdraw(first, key, new Money(1000));
        atm.ejectCard(first);
        var second = atm.insertCard(C2);
        reject(atm, INVALID_SESSION_STATE, () -> atm.withdraw(second, key, new Money(1000)));
        atm.enterPin(second, Pin.parse("4321"));
        var before = atm.snapshot();
        assertSame(receipt, atm.withdraw(second, key, new Money(1000)));
        assertSame(before, atm.snapshot());
        assertEquals(1, atm.history(second).size());
    }

    @Test
    void keyScopeSeparatesAccountsAndFailuresDoNotReserveKey() {
        var atm = timed(new MutableClock());
        var key = new OperationKey("one");
        var first = atm.insertCard(C1);
        atm.enterPin(first, Pin.parse("1234"));
        reject(atm, INSUFFICIENT_FUNDS, () -> atm.withdraw(first, key, new Money(101000)));
        assertTrue(atm.snapshot().operations().isEmpty());
        var receipt = atm.withdraw(first, key, new Money(1000));
        atm.ejectCard(first);
        var second = atm.insertCard(C2);
        atm.enterPin(second, Pin.parse("4321"));
        var other = atm.withdraw(second, key, new Money(2000));
        assertNotEquals(receipt.id(), other.id());
        assertEquals(A2, other.account());
        assertEquals(2, atm.snapshot().operations().size());
    }

    @Test
    void failedCompositionCanReuseKeyWithCorrectedAmountAndUnkeyedCallsRemainNewIntents() {
        var atm = atm(new CashInventory(Map.of(Banknote.PLN_50, 3)), auth());
        var id = atm.insertCard(C1);
        atm.enterPin(id, Pin.parse("1234"));
        var key = new OperationKey("one");
        reject(atm, CANNOT_DISPENSE, () -> atm.withdraw(id, key, new Money(6000)));
        var first = atm.withdraw(id, key, new Money(5000));
        var second = atm.withdraw(id, new Money(5000));
        var third = atm.withdraw(id, new Money(5000));
        assertNotEquals(first.id(), second.id());
        assertNotEquals(second.id(), third.id());
        assertEquals(3, atm.snapshot().withdrawals().size());
        assertEquals(3, atm.snapshot().operations().size());
    }

    @Test
    void newKnownCardCanAtomicallyReplaceExpiredSessionWhileUnknownCardCannotPublish() {
        var clock = new MutableClock();
        var atm = timed(clock);
        var old = atm.insertCard(C1);
        clock.at(NOW.plus(TTL));
        var before = atm.snapshot();
        reject(atm, UNKNOWN_CARD, () -> atm.insertCard(new CardId("X")));
        assertSame(before, atm.snapshot());
        var current = atm.insertCard(C2);
        assertNotEquals(old, current);
        assertEquals(NOW.plus(TTL).plus(TTL), ((Session.Active) atm.snapshot().session()).expiresAt());
        reject(atm, STALE_SESSION, () -> atm.ejectCard(old));
    }

    @Test
    void invalidTtlAndOverflowDoNotCreatePartialSession() {
        for (Duration ttl : List.of(Duration.ZERO, Duration.ofNanos(-1))) {
            assertThrows(IllegalArgumentException.class, () -> new Atm(List.of(new Account(A1, new Money(1))),
                    Map.of(C1, A1), cash(), auth(), CLOCK, ttl));
        }
        var atm = new Atm(List.of(new Account(A1, new Money(1))), Map.of(C1, A1), cash(), auth(),
                Clock.fixed(Instant.MAX.minusSeconds(1), ZoneOffset.UTC), Duration.ofSeconds(2));
        var before = atm.snapshot();
        assertThrows(java.time.DateTimeException.class, () -> atm.insertCard(C1));
        assertSame(before, atm.snapshot());
    }

    @Test
    void validOperationReadsClockOnceAndStaleTokenDoesNotReadClock() {
        var reads = new AtomicInteger();
        Clock counting = new Clock() {

            @Override
            public Instant instant() {
                reads.incrementAndGet();
                return NOW;
            }

            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return Clock.fixed(NOW, zone);
            }
        };
        var atm = new Atm(List.of(new Account(A1, new Money(10000))), Map.of(C1, A1), cash(), auth(), counting, TTL);
        var id = atm.insertCard(C1);
        assertEquals(1, reads.get());
        atm.enterPin(id, Pin.parse("1234"));
        assertEquals(2, reads.get());
        atm.withdraw(id, new OperationKey("one"), new Money(1000));
        assertEquals(3, reads.get());
        atm.withdraw(id, new OperationKey("one"), new Money(1000));
        assertEquals(4, reads.get());
        reject(atm, STALE_SESSION, () -> atm.ejectCard(new SessionId(UUID.randomUUID())));
        assertEquals(4, reads.get());
    }

    @Test
    void keysAndSessionIdentifiersHaveValueSemanticsAndOldRegistryIsImmutable() {
        var id = new SessionId(UUID.randomUUID());
        assertEquals(id, SessionId.parse(" " + id.value() + " "));
        assertThrows(IllegalArgumentException.class, () -> SessionId.parse("bad-id"));
        assertEquals(new OperationKey("one"), new OperationKey(" one "));
        assertNotEquals(new OperationKey("one"), new OperationKey("ONE"));
        for (String value : List.of("", "a b", "ą", "x".repeat(65))) {
            assertThrows(IllegalArgumentException.class, () -> new OperationKey(value));
        }
        var atm = atm();
        var session = atm.insertCard(C1);
        atm.enterPin(session, Pin.parse("1234"));
        var before = atm.snapshot();
        atm.withdraw(session, new OperationKey("one"), new Money(1000));
        assertTrue(before.operations().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> atm.snapshot().operations().clear());
    }

    private static Atm timed(Clock clock) {
        return new Atm(List.of(new Account(A1, new Money(100000)), new Account(A2, new Money(40000))), Map.of(C1, A1,
                C2, A2), cash(), auth(), clock, TTL);
    }

    private static void reject(Atm atm, AtmException.Reason reason, Runnable command) {
        var before = atm.snapshot();
        assertEquals(reason, assertThrows(AtmException.class, command::run).reason());
        assertSame(before, atm.snapshot());
    }
}

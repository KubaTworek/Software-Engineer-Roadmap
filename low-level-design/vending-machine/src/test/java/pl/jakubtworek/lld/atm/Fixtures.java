package pl.jakubtworek.lld.atm;

import pl.jakubtworek.lld.atm.console.DemoAuthenticator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Fikcyjne dane testowe, bez zależności od innych ćwiczeń i rzeczywistych kont.
 */
public final class Fixtures {

    public static final AccountId A1 = new AccountId("A1"), A2 = new AccountId("A2");
    public static final CardId C1 = new CardId("C1"), C2 = new CardId("C2");
    public static final Instant NOW = Instant.parse("2030-01-01T08:00:00Z");
    public static final Duration TTL = Duration.ofMinutes(5);
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private Fixtures() {
    }

    public static CardAuthenticator auth() {
        return new DemoAuthenticator(Map.of(C1, Pin.parse("1234"), C2, Pin.parse("4321")));
    }

    public static CashInventory cash() {
        return new CashInventory(Map.of(Banknote.PLN_10, 10, Banknote.PLN_20, 5, Banknote.PLN_50, 2, Banknote.PLN_100,
                1, Banknote.PLN_200, 1));
    }

    public static Atm atm() {
        return atm(cash(), auth());
    }

    public static Atm atm(CashInventory cash, CardAuthenticator auth) {
        return new Atm(List.of(new Account(A1, new Money(100000)), new Account(A2, new Money(40000))), Map.of(C1, A1,
                C2, A2), cash, auth, CLOCK, TTL);
    }

    public static void login(Atm atm) {
        atm.insertCard(C1);
        atm.enterPin(sid(atm), Pin.parse("1234"));
    }

    /**
     * Pomocnik starych scenariuszy: nieistniejący token służy wyłącznie próbom z Idle; nowe testy przechowują ID jawnie.
     */
    public static SessionId sid(Atm atm) {
        return atm.snapshot().session() instanceof Session.Active active ? active.id() : new SessionId(new UUID(0, 0));
    }

    /**
     * Kontrolowany zegar bez czekania; produkcyjna logika nie ma dostępu do setterów testu.
     */
    public static final class MutableClock extends Clock {

        private Instant current = NOW;

        public void at(Instant instant) {
            current = instant;
        }

        @Override
        public Instant instant() {
            return current;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return Clock.fixed(current, zone);
        }
    }
}

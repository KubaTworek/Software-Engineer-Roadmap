package pl.jakubtworek.lld.atm;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.atm.Fixtures.*;

/**
 * Granice wartości są sprawdzane także poza przepływem bankomatu, bez testowania samych getterów.
 */
class ValueObjectsTest {

    @Test
    void moneyPreservesGroszAndRejectsUnderflowOrOverflow() {
        assertEquals("0.01 PLN", new Money(1).toString());
        assertEquals("92233720368547758.07 PLN", new Money(Long.MAX_VALUE).toString());
        assertThrows(IllegalArgumentException.class, () -> new Money(-1));
        assertThrows(IllegalArgumentException.class, () -> new Money(1).minus(new Money(2)));
        assertThrows(ArithmeticException.class, () -> new Money(Long.MAX_VALUE).plus(new Money(1)));
    }

    @Test
    void pinCopiesInputAndMasksPrintingWhileIdentifiersNormalize() {
        char[] source = "1234".toCharArray();
        var pin = new Pin(source);
        source[0] = '9';
        assertTrue(pin.matches(Pin.parse("1234")));
        assertFalse(pin.matches(Pin.parse("9234")));
        assertEquals("****", pin.toString());
        for (String text : new String[] { "123", "12345", "12a4", "１２３４" }) {
            assertThrows(IllegalArgumentException.class, () -> Pin.parse(text));
        }
        assertEquals(C1, new CardId(" c1 "));
        assertEquals(A1, new AccountId(" a1 "));
        assertThrows(IllegalArgumentException.class, () -> new CardId("a b"));
        assertThrows(IllegalArgumentException.class, () -> new AccountId(""));
        assertThrows(NullPointerException.class, () -> Pin.parse(null));
    }

    @Test
    void cashCopiesSourcesAndRejectsInvalidCountsAndUnavailableRemoval() {
        var source = new HashMap<>(Map.of(Banknote.PLN_10, 2));
        var stock = new CashInventory(source);
        var bundle = new CashBundle(source);
        source.clear();
        assertEquals(new Money(2000), stock.total());
        assertEquals(new Money(2000), bundle.total());
        assertTrue(stock.without(bundle).notes().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> stock.notes().clear());
        assertThrows(UnsupportedOperationException.class, () -> bundle.notes().clear());
        assertThrows(IllegalArgumentException.class, () -> new CashInventory(Map.of(Banknote.PLN_10, -1)));
        assertThrows(IllegalArgumentException.class, () -> new CashBundle(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new CashBundle(Map.of(Banknote.PLN_10, 0)));
        assertThrows(IllegalArgumentException.class, () -> stock.without(new CashBundle(Map.of(Banknote.PLN_10, 3))));
    }

    @Test
    void accountReceiptAndSessionCannotExpressInvalidLocalValues() {
        var account = new Account(A1, new Money(Long.MAX_VALUE));
        assertEquals(new Money(Long.MAX_VALUE - 1), account.debit(new Money(1)).balance());
        var bundle = new CashBundle(Map.of(Banknote.PLN_10, 1));
        assertThrows(IllegalArgumentException.class, () -> new WithdrawalReceipt(UUID.randomUUID(), A1, new Money(2000),
                bundle, new Money(0)));
        var id = new SessionId(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> new Session.CardInserted(id, C1, 3, NOW.plus(TTL)));
        assertThrows(IllegalArgumentException.class, () -> new Session.CardInserted(id, C1, -1, NOW.plus(TTL)));
        assertThrows(NullPointerException.class, () -> new Session.Authenticated(id, C1, null, NOW.plus(TTL)));
    }
}

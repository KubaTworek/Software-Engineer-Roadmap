package pl.jakubtworek.lld.parkinglot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Sprawdzamy znaczenie wartości i odrzucanie niemożliwego stanu, nie gettery.
 * W szczególności normalizacja rejestracji wpływa na niezmiennik tożsamości,
 * a dokładne grosze i jawne przepełnienie chronią regułę należności.
 */
class ValueObjectsTest {

    @Test
    void plateNormalizationAndMoneyAreExact() {
        assertEquals(new LicensePlate("WX123"), new LicensePlate(" wx123 "));
        assertEquals("5.01 PLN", new Money(501).toString());
        assertEquals(new Money(1503), new Money(501).times(3));
        assertThrows(ArithmeticException.class, () -> new Money(Long.MAX_VALUE).times(2));
        assertThrows(IllegalArgumentException.class, () -> new Money(-1));
        assertThrows(IllegalArgumentException.class, () -> new Money(1).times(-1));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "   ", "WX 123", "A/B", "ABCDEFGHIJKLMNOPQRSTU" })
    void simplifiedPlateFormatHasExplicitLimits(String text) {
        assertThrows(IllegalArgumentException.class, () -> new LicensePlate(text));
    }

    @Test
    void spotAndTicketIdentitiesMustBeValid() {
        assertThrows(IllegalArgumentException.class, () -> new ParkingSpot(" ", 0, SpotType.LARGE));
        assertThrows(IllegalArgumentException.class, () -> new ParkingSpot("A", -1, SpotType.LARGE));
        assertThrows(IllegalArgumentException.class, () -> new TicketId(UUID.randomUUID(), 0));
    }

    @Test
    void receiptCannotDescribeExitBeforeEntry() {
        var id = new TicketId(UUID.randomUUID(), 1);
        Instant at = Instant.parse("2026-01-01T10:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> new ParkingReceipt(id, at, at.minusSeconds(1),
                new Money(0)));
    }
}

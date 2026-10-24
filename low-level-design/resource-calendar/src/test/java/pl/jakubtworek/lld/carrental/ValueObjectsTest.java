package pl.jakubtworek.lld.carrental;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.carrental.Fixtures.*;

/**
 * Wartości mają chronić tożsamość i arytmetykę, także poza koordynatorem.
 */
class ValueObjectsTest {

    @ParameterizedTest
    @ValueSource(strings = { "", " ", "A B", "A/B", "ą", "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567" })
    void invalidCodesAreRejected(String value) {
        assertThrows(IllegalArgumentException.class, () -> new VehicleId(value));
        assertThrows(IllegalArgumentException.class, () -> new CustomerId(value));
    }

    @Test
    void normalizedCodesExposeDuplicatesAndUuidRoundTrips() {
        assertEquals(V1, new VehicleId(" v1 "));
        assertEquals(U1, new CustomerId(" u1 "));
        var id = new ReservationId(UUID.randomUUID());
        assertEquals(id, ReservationId.parse(" " + id.value() + " "));
        var service = new MaintenanceId(UUID.randomUUID());
        assertEquals(service, MaintenanceId.parse(service.value().toString()));
        assertThrows(IllegalArgumentException.class, () -> ReservationId.parse("bad"));
        assertThrows(NullPointerException.class, () -> new ReservationId(null));
        assertThrows(NullPointerException.class, () -> new VehicleId(null));
        var vehicle = new Vehicle(V1, "A", VehicleCategory.ECONOMY);
        assertThrows(IllegalArgumentException.class, () -> new RentalAgency(List.of(vehicle, vehicle), List.of(),
                new TestClock()));
        var customer = new Customer(U1, "A");
        assertThrows(IllegalArgumentException.class, () -> new RentalAgency(List.of(), List.of(customer, customer),
                new TestClock()));
        assertTrue(new RentalAgency(List.of(), List.of(), new TestClock()).snapshot().vehicles().isEmpty());
    }

    @Test
    void moneyAndReceiptRejectOverflowInsteadOfPublishingInvalidTotal() {
        assertEquals("123.45 PLN", new Money(12345).toString());
        assertEquals(new Money(30000), new Money(10000).times(3));
        assertThrows(IllegalArgumentException.class, () -> new Money(-1));
        assertThrows(IllegalArgumentException.class, () -> new Money(1).times(-1));
        assertThrows(ArithmeticException.class, () -> new Money(Long.MAX_VALUE).times(2));
        assertThrows(ArithmeticException.class, () -> new Money(Long.MAX_VALUE).plus(new Money(1)));
        assertThrows(ArithmeticException.class, () -> new RentalReceipt(new ReservationId(UUID.randomUUID()), TODAY,
                new Money(Long.MAX_VALUE), new Money(1)));
    }
}

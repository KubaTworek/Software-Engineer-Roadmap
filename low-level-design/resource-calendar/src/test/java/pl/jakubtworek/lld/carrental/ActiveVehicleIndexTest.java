package pl.jakubtworek.lld.carrental;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.carrental.Fixtures.*;

/**
 * Indeks fizycznego wydania i kalendarz przyszłych planów mają odrębne cykle.
 */
class ActiveVehicleIndexTest {

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void changingFutureReservationDoesNotForgetAnotherActiveRental(boolean reschedule) {
        var clock = new TestClock();
        var agency = agency(clock);
        var active = agency.reserve(U1, V1, period(0, 1));
        var future = agency.reserve(U2, V1, period(2, 3));
        var next = agency.reserve(U2, V1, period(4, 5));
        agency.pickup(U1, active.id());
        if (reschedule) {
            agency.reschedule(U2, future.id(), period(2, 4));
        } else {
            agency.cancel(U2, future.id());
        }
        clock.today = TODAY.plusDays(4);
        var before = agency.snapshot();
        var exception = assertThrows(RentalException.class, () -> agency.pickup(U2, next.id()));
        assertEquals(RentalException.Reason.VEHICLE_NOT_RETURNED, exception.reason());
        assertSame(before, agency.snapshot());
        agency.returnVehicle(U1, active.id());
        assertEquals(ReservationStatus.ACTIVE, agency.pickup(U2, next.id()).status());
    }
}

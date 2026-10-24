package pl.jakubtworek.lld.carrental;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.carrental.Fixtures.*;

/**
 * Diagnostyka też ma kontrakt: źródło jest konkretne, stabilne i zgodne z odmową komendy.
 */
class CalendarConflictTest {

    @Test
    void reservationBeforeMaintenanceThenEarliestStartRegardlessOfInsertionOrder() {
        var agency = agency(new TestClock());
        var later = agency.reserve(U1, V1, period(5, 7));
        var earlier = agency.reserve(U2, V1, period(3, 4));
        var service = agency.scheduleMaintenance(V1, period(0, 2), "Przegląd");
        var expected = new CalendarConflict.ReservationConflict(earlier.id(), V1, period(3, 4));
        assertEquals(expected, agency.snapshot().conflict(V1, period(0, 7)).orElseThrow());
        var error = assertThrows(RentalException.class, () -> agency.reserve(U1, V1, period(0, 7)));
        assertEquals(expected, error.conflict().orElseThrow());
        assertEquals(expected.reason(), error.reason());
        agency.cancel(U2, earlier.id());
        assertEquals(later.id(), ((CalendarConflict.ReservationConflict) agency.snapshot().conflict(V1, period(0, 7))
                .orElseThrow()).id());
        agency.cancel(U1, later.id());
        assertEquals(new CalendarConflict.MaintenanceConflict(service.id(), V1, period(0, 2)), agency.snapshot()
                .conflict(V1, period(0, 7)).orElseThrow());
        agency.removeMaintenance(service.id());
        assertTrue(agency.snapshot().conflict(V1, period(0, 7)).isEmpty());
    }

    @Test
    void maintenanceSelectionIsEarliestAndOldDescriptionRemainsStable() {
        var agency = agency(new TestClock());
        agency.scheduleMaintenance(V1, period(5, 7), "Późniejszy");
        var earlier = agency.scheduleMaintenance(V1, period(1, 3), "Wcześniejszy");
        var old = agency.snapshot();
        assertEquals(earlier.id().value(), old.conflict(V1, period(0, 7)).orElseThrow().sourceId());
        agency.removeMaintenance(earlier.id());
        assertEquals(earlier.id().value(), old.conflict(V1, period(0, 7)).orElseThrow().sourceId());
        assertThrows(NullPointerException.class, () -> old.conflict(null, period(0, 1)));
        assertThrows(NullPointerException.class, () -> old.conflict(V1, null));
        assertEquals(RentalException.Reason.UNKNOWN_VEHICLE, assertThrows(RentalException.class,
                () -> old.conflict(new VehicleId("X"), period(0, 1))).reason());
        assertTrue(assertThrows(RentalException.class, () -> agency.cancel(U1,
                new ReservationId(java.util.UUID.randomUUID()))).conflict().isEmpty());
    }
}

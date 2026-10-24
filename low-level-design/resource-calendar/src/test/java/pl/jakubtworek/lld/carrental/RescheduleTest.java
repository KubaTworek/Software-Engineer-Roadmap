package pl.jakubtworek.lld.carrental;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.carrental.Fixtures.*;
import static pl.jakubtworek.lld.carrental.RentalException.Reason.*;

/**
 * Zmiana terminu ma zachować umowę, a nie realizować podatne na awarię cancel+reserve.
 */
class RescheduleTest {

    @Test
    void overlappingOwnPeriodCanMoveAndRepriceWithoutChangingIdentityOrRate() {
        var agency = agency(new TestClock());
        var original = agency.reserve(U1, V1, period(1, 4));
        var before = agency.snapshot();
        var changed = agency.reschedule(U1, original.id(), period(2, 6));
        assertEquals(original.id(), changed.id());
        assertEquals(original.vehicleId(), changed.vehicleId());
        assertEquals(original.customerId(), changed.customerId());
        assertEquals(original.dailyRate(), changed.dailyRate());
        assertEquals(new Money(40000), changed.quotedTotal());
        assertEquals(ReservationStatus.RESERVED, changed.status());
        assertEquals(1, agency.snapshot().reservations().size());
        assertTrue(agency.snapshot().isAvailable(V1, period(1, 2)));
        assertFalse(agency.snapshot().isAvailable(V1, period(4, 6)));
        assertSame(original, before.reservations().get(original.id()));
        assertEquals(period(1, 4), original.period());
        assertEquals(new Money(30000), original.quotedTotal());
        assertEquals(new Money(10000), agency.reschedule(U1, changed.id(), period(0, 1)).quotedTotal());
    }

    @Test
    void conflictKeepsOldPeriodAndReportsTypedSourceFromSameCustomerToo() {
        var agency = agency(new TestClock());
        var own = agency.reserve(U1, V1, period(0, 2));
        var other = agency.reserve(U1, V1, period(3, 5));
        var before = agency.snapshot();
        var error = assertThrows(RentalException.class, () -> agency.reschedule(U1, own.id(), period(1, 4)));
        assertEquals(RESERVATION_CONFLICT, error.reason());
        assertEquals(new CalendarConflict.ReservationConflict(other.id(), V1, other.period()), error.conflict()
                .orElseThrow());
        assertSame(before, agency.snapshot());
        var block = agency.scheduleMaintenance(V1, period(5, 7), "Przegląd");
        before = agency.snapshot();
        error = assertThrows(RentalException.class, () -> agency.reschedule(U1, own.id(), period(5, 6)));
        assertEquals(new CalendarConflict.MaintenanceConflict(block.id(), V1, block.period()), error.conflict()
                .orElseThrow());
        assertSame(before, agency.snapshot());
    }

    @Test
    void unchangedPeriodAndAdjacentOtherBookingAreAllowed() {
        var agency = agency(new TestClock());
        var own = agency.reserve(U1, V1, period(0, 2));
        agency.reserve(U2, V1, period(4, 6));
        assertEquals(period(0, 2), agency.reschedule(U1, own.id(), period(0, 2)).period());
        assertEquals(period(2, 4), agency.reschedule(U1, own.id(), period(2, 4)).period());
    }

    @Test
    void onlyReservedOwnedKnownContractMayBeMovedAndClockFailuresKeepIt() {
        var clock = new TestClock();
        var agency = agency(clock);
        var own = agency.reserve(U1, V1, period(0, 2));
        var before = agency.snapshot();
        assertEquals(NOT_CUSTOMER, assertThrows(RentalException.class, () -> agency.reschedule(U2, own.id(), period(2,
                4))).reason());
        assertEquals(PAST_PERIOD, assertThrows(RentalException.class, () -> agency.reschedule(U1, own.id(), period(-1,
                1))).reason());
        assertThrows(NullPointerException.class, () -> agency.reschedule(U1, own.id(), null));
        assertThrows(NullPointerException.class, () -> agency.reschedule(null, own.id(), period(0, 1)));
        assertThrows(NullPointerException.class, () -> agency.reschedule(U1, null, period(0, 1)));
        assertEquals(UNKNOWN_RESERVATION, assertThrows(RentalException.class, () -> agency.reschedule(U1,
                new ReservationId(java.util.UUID.randomUUID()), period(0, 1))).reason());
        clock.failure = new IllegalStateException("Awaria czasu");
        assertThrows(IllegalStateException.class, () -> agency.reschedule(U1, own.id(), period(1, 2)));
        assertSame(before, agency.snapshot());
        clock.failure = null;
        agency.pickup(U1, own.id());
        assertEquals(INVALID_STATUS, assertThrows(RentalException.class, () -> agency.reschedule(U1, own.id(), period(1,
                3))).reason());
        agency.returnVehicle(U1, own.id());
        assertEquals(INVALID_STATUS, assertThrows(RentalException.class, () -> agency.reschedule(U1, own.id(), period(1,
                3))).reason());
        var cancelled = agency.reserve(U1, V1, period(0, 1));
        agency.cancel(U1, cancelled.id());
        before = agency.snapshot();
        assertEquals(INVALID_STATUS, assertThrows(RentalException.class, () -> agency.reschedule(U1, cancelled.id(),
                period(1, 3))).reason());
        assertSame(before, agency.snapshot());
    }
}

package pl.jakubtworek.lld.carrental;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.carrental.Fixtures.*;
import static pl.jakubtworek.lld.carrental.RentalException.Reason.*;

/**
 * Scenariusze chronią kalendarz i faktyczne przekazanie zasobu, także po odmowie.
 */
class RentalAgencyTest {

    @Test
    void reservationCapturesPriceAndBlocksOnlyItsVehicleAndPeriod() {
        var clock = new TestClock();
        var agency = agency(clock);
        var before = agency.snapshot();
        var result = agency.reserve(U1, V1, period(0, 3));
        assertEquals(1, clock.reads);
        assertEquals(new Money(30000), result.quotedTotal());
        assertEquals(new Money(10000), result.dailyRate());
        assertEquals(ReservationStatus.RESERVED, result.status());
        assertFalse(agency.snapshot().isAvailable(V1, period(1, 2)));
        assertTrue(agency.snapshot().isAvailable(V2, period(1, 2)));
        assertTrue(before.isAvailable(V1, period(1, 2)));
        reject(agency, RESERVATION_CONFLICT, () -> agency.reserve(U2, V1, period(1, 4)));
        // Sąsiednie przedziały są poprawne.
        agency.reserve(U2, V1, period(3, 5));
        assertEquals(new Money(60000), agency.reserve(U1, V2, period(0, 3)).quotedTotal());
    }

    @Test
    void cancellationFreesPeriodAndOldCommandCannotAffectNewReservation() {
        var agency = agency(new TestClock());
        var first = agency.reserve(U1, V1, period(0, 2));
        var old = agency.snapshot();
        agency.cancel(U1, first.id());
        var second = agency.reserve(U1, V1, period(0, 2));
        assertNotEquals(first.id(), second.id());
        reject(agency, INVALID_STATUS, () -> agency.cancel(U1, first.id()));
        reject(agency, INVALID_STATUS, () -> agency.pickup(U1, first.id()));
        reject(agency, INVALID_STATUS, () -> agency.returnVehicle(U1, first.id()));
        assertSame(second, agency.snapshot().reservations().get(second.id()));
        assertEquals(ReservationStatus.RESERVED, old.reservations().get(first.id()).status());
    }

    @Test
    void pickupMustBeOnStartDateAndNoShowCanStillBeCancelled() {
        var clock = new TestClock();
        var agency = agency(clock);
        var reservation = agency.reserve(U1, V1, period(1, 3));
        reject(agency, PICKUP_DATE_MISMATCH, () -> agency.pickup(U1, reservation.id()));
        clock.today = TODAY.plusDays(2);
        reject(agency, PICKUP_DATE_MISMATCH, () -> agency.pickup(U1, reservation.id()));
        agency.cancel(U1, reservation.id());
        assertTrue(agency.snapshot().isAvailable(V1, period(2, 3)));
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 3, 4, 6 })
    void earlyOnTimeAndLateReturnsUsePinnedDailyRate(int returnDay) {
        var clock = new TestClock();
        var agency = agency(clock);
        var reservation = agency.reserve(U1, V1, period(0, 3));
        agency.pickup(U1, reservation.id());
        var active = agency.snapshot();
        clock.today = TODAY.plusDays(returnDay);
        var result = agency.returnVehicle(U1, reservation.id());
        assertEquals(new Money(30000), result.plannedCharge());
        assertEquals(new Money(Math.max(0, returnDay - 3) * 10000L), result.lateCharge());
        assertEquals(new Money(Math.max(3, returnDay) * 10000L), result.total());
        assertEquals(clock.today, result.returnedOn());
        assertEquals(ReservationStatus.COMPLETED, agency.snapshot().reservations().get(reservation.id()).status());
        assertSame(result, agency.snapshot().reservations().get(reservation.id()).receipt().orElseThrow());
        assertEquals(ReservationStatus.ACTIVE, active.reservations().get(reservation.id()).status());
        assertTrue(agency.snapshot().isAvailable(V1, period(0, 3)));
        reject(agency, INVALID_STATUS, () -> agency.returnVehicle(U1, reservation.id()));
    }

    @Test
    void plannedAvailabilityDoesNotGuaranteePickupBeforePreviousPhysicalReturn() {
        var clock = new TestClock();
        var agency = agency(clock);
        var first = agency.reserve(U1, V1, period(0, 2));
        var second = agency.reserve(U2, V1, period(2, 4));
        agency.pickup(U1, first.id());
        clock.today = TODAY.plusDays(2);
        reject(agency, VEHICLE_NOT_RETURNED, () -> agency.pickup(U2, second.id()));
        assertTrue(agency.snapshot().isAvailable(V1, period(4, 5)), "Kwerenda opisuje plan, nie fizyczne posiadanie");
        agency.returnVehicle(U1, first.id());
        agency.pickup(U2, second.id());
        assertEquals(1, agency.snapshot().reservations().values().stream()
                .filter(r -> r.status() == ReservationStatus.ACTIVE).count());
    }

    @Test
    void activeRentalCannotBeCancelledOrPickedUpTwice() {
        var agency = agency(new TestClock());
        var reservation = agency.reserve(U1, V1, period(0, 2));
        reject(agency, INVALID_STATUS, () -> agency.returnVehicle(U1, reservation.id()));
        agency.pickup(U1, reservation.id());
        reject(agency, INVALID_STATUS, () -> agency.pickup(U1, reservation.id()));
        reject(agency, INVALID_STATUS, () -> agency.cancel(U1, reservation.id()));
    }

    @Test
    void maintenanceAndReservationsRejectConflictsInBothDirections() {
        var agency = agency(new TestClock());
        var block = agency.scheduleMaintenance(V1, period(1, 3), " Przegląd ");
        assertEquals("Przegląd", block.reason());
        reject(agency, MAINTENANCE_CONFLICT, () -> agency.reserve(U1, V1, period(0, 2)));
        reject(agency, MAINTENANCE_CONFLICT, () -> agency.scheduleMaintenance(V1, period(2, 4), "Naprawa"));
        agency.reserve(U1, V1, period(3, 5));
        reject(agency, RESERVATION_CONFLICT, () -> agency.scheduleMaintenance(V1, period(4, 6), "Naprawa"));
        var before = agency.snapshot();
        assertSame(block, agency.removeMaintenance(block.id()));
        assertTrue(agency.snapshot().isAvailable(V1, period(1, 3)));
        assertFalse(before.isAvailable(V1, period(1, 3)));
        reject(agency, UNKNOWN_MAINTENANCE, () -> agency.removeMaintenance(block.id()));
    }

    @Test
    void pastPlansAreRejectedButReadQueriesMayDescribePastDates() {
        var agency = agency(new TestClock());
        reject(agency, PAST_PERIOD, () -> agency.reserve(U1, V1, period(-1, 1)));
        reject(agency, PAST_PERIOD, () -> agency.scheduleMaintenance(V1, period(-1, 1), "Przegląd"));
        assertTrue(agency.snapshot().isAvailable(V1, period(-2, -1)));
    }

    @Test
    void unknownReferencesOwnershipAndNullInputsLeaveSamePublication() {
        var agency = agency(new TestClock());
        var reservation = agency.reserve(U1, V1, period(0, 1));
        var missing = new ReservationId(UUID.randomUUID());
        reject(agency, UNKNOWN_CUSTOMER, () -> agency.reserve(new CustomerId("X"), V1, period(0, 1)));
        reject(agency, UNKNOWN_VEHICLE, () -> agency.reserve(U1, new VehicleId("X"), period(0, 1)));
        reject(agency, UNKNOWN_RESERVATION, () -> agency.pickup(U1, missing));
        reject(agency, NOT_CUSTOMER, () -> agency.cancel(U2, reservation.id()));
        reject(agency, NOT_CUSTOMER, () -> agency.pickup(U2, reservation.id()));
        reject(agency, NOT_CUSTOMER, () -> agency.returnVehicle(U2, reservation.id()));
        var before = agency.snapshot();
        List<Runnable> invalid = List.of(() -> agency.reserve(null, V1, period(0, 1)), () -> agency.reserve(U1, null,
                period(0, 1)), () -> agency.reserve(U1, V1, null), () -> agency.cancel(null, reservation.id()),
                () -> agency.pickup(U1, null), () -> agency.returnVehicle(null, reservation.id()),
                () -> agency.scheduleMaintenance(V1, period(0, 1), null), () -> agency.removeMaintenance(null));
        for (Runnable action : invalid) {
            assertThrows(NullPointerException.class, action::run);
            assertSame(before, agency.snapshot());
        }
    }

    @Test
    void clockFailureDoesNotPublishAndCancellationDoesNotNeedClock() {
        var clock = new TestClock();
        var agency = agency(clock);
        var reservation = agency.reserve(U1, V1, period(0, 2));
        clock.failure = new IllegalStateException("Awaria czasu");
        var before = agency.snapshot();
        assertThrows(IllegalStateException.class, () -> agency.reserve(U2, V2, period(0, 2)));
        assertThrows(IllegalStateException.class, () -> agency.pickup(U1, reservation.id()));
        assertThrows(IllegalStateException.class, () -> agency.scheduleMaintenance(V2, period(0, 2), "Przegląd"));
        assertSame(before, agency.snapshot());
        clock.failure = null;
        agency.pickup(U1, reservation.id());
        before = agency.snapshot();
        clock.failure = new IllegalStateException("Awaria czasu");
        assertThrows(IllegalStateException.class, () -> agency.returnVehicle(U1, reservation.id()));
        assertSame(before, agency.snapshot());
        var otherClock = new TestClock();
        var other = agency(otherClock);
        var uncollected = other.reserve(U1, V1, period(0, 1));
        otherClock.failure = new IllegalStateException("Awaria czasu");
        assertDoesNotThrow(() -> other.cancel(U1, uncollected.id()));
    }

    @Test
    void reversedClockCannotReturnBeforePickup() {
        var clock = new TestClock();
        var agency = agency(clock);
        var reservation = agency.reserve(U1, V1, period(0, 2));
        agency.pickup(U1, reservation.id());
        var before = agency.snapshot();
        clock.today = TODAY.minusDays(1);
        assertThrows(IllegalStateException.class, () -> agency.returnVehicle(U1, reservation.id()));
        assertSame(before, agency.snapshot());
    }

    @Test
    void clockZoneDefinesCalendarDate() {
        var clock = Clock.fixed(Instant.parse("2026-10-18T23:00:00Z"), ZoneOffset.ofHours(2));
        var agency = agency(clock);
        agency.pickup(U1, agency.reserve(U1, V1, period(0, 1)).id());
    }

    @Test
    void inputCollectionsAndHistoricalViewsCannotBeChangedFromOutside() {
        var vehicles = new ArrayList<>(List.of(new Vehicle(V1, "A", VehicleCategory.ECONOMY)));
        var customers = new ArrayList<>(List.of(new Customer(U1, "A")));
        var agency = new RentalAgency(vehicles, customers, new TestClock());
        vehicles.clear();
        customers.clear();
        var before = agency.snapshot();
        assertThrows(UnsupportedOperationException.class, () -> before.vehicles().clear());
        assertThrows(UnsupportedOperationException.class, () -> before.customers().clear());
        assertThrows(UnsupportedOperationException.class, () -> before.reservations().clear());
        assertThrows(UnsupportedOperationException.class, () -> before.maintenance().clear());
        assertThrows(UnsupportedOperationException.class, () -> before.availableVehicles(period(0, 1)).clear());
        agency.reserve(U1, V1, period(0, 1));
        assertTrue(before.reservations().isEmpty());
    }

    private static void reject(RentalAgency agency, RentalException.Reason reason, Runnable action) {
        var before = agency.snapshot();
        assertEquals(reason, assertThrows(RentalException.class, action::run).reason());
        assertSame(before, agency.snapshot());
    }
}

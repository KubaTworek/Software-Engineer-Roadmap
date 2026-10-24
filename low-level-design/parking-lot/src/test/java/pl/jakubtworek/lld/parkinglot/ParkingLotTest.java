package pl.jakubtworek.lld.parkinglot;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.parkinglot.Fixtures.*;
import static pl.jakubtworek.lld.parkinglot.ParkingException.Reason.*;
import static pl.jakubtworek.lld.parkinglot.VehicleType.*;

/**
 * Specyfikacja zachowania granicy spójności. Odczytujemy publiczne widoki,
 * a nie prywatne mapy. Sprawdzamy skutek odmowy i pełną sekwencję operacji,
 * bo sam wyjątek nie dowodzi, że miejsce i bilet pozostały nienaruszone.
 */
class ParkingLotTest {

    @Test
    void entryExitReleasesSpotAndPreservesOldTicketAndSnapshot() {
        var clock = new TestClock();
        var parking = parking(new BestFitAllocation(), hourly(), clock);
        var empty = parking.snapshot();
        var open = parking.enter(vehicle("WX123", CAR));
        var occupied = parking.snapshot();
        assertEquals("C1", open.spot().id());
        assertEquals(2, occupied.availableSpots().size());
        clock.advance(Duration.ofMinutes(70));
        var receipt = parking.exit(open.id());
        assertEquals(new Money(500), receipt.fee());
        assertEquals(open.enteredAt(), receipt.enteredAt());
        assertEquals(clock.instant(), receipt.exitedAt());
        assertEquals(3, parking.snapshot().availableSpots().size());
        assertTrue(parking.snapshot().activeTickets().isEmpty());
        assertFalse(parking.snapshot().tickets().get(open.id()).isActive());
        assertSame(receipt, parking.snapshot().tickets().get(open.id()).receipt().orElseThrow());
        // Dawny bilet nadal opisuje stan z chwili wydania. Zamknięcie zastępuje
        // wartość w nowej mapie; nie przepisuje obiektu trzymanego przez klienta.
        assertTrue(open.isActive());
        assertEquals(1, occupied.activeTickets().size());
        assertEquals(3, empty.availableSpots().size());
        assertTrue(empty.tickets().isEmpty());
    }

    @Test
    void duplicatePlateIsRejectedEvenWithDifferentTypeAndDoesNotConsumeNumber() {
        var parking = parking(new BestFitAllocation(), hourly(), new TestClock());
        parking.enter(vehicle(" wx123 ", CAR));
        reject(parking, VEHICLE_ALREADY_PARKED, () -> parking.enter(vehicle("WX123", MOTORCYCLE)));
        assertEquals(2, parking.enter(vehicle("MOTO", MOTORCYCLE)).id().number());
    }

    @Test
    void noCompatibleSpotIsDifferentFromNoFreeSpots() {
        var parking = parking(new FirstAvailableAllocation(), hourly(), new TestClock());
        parking.enter(vehicle("CAR", CAR));
        assertEquals(2, parking.snapshot().availableSpots().size());
        reject(parking, NO_COMPATIBLE_SPOT, () -> parking.enter(vehicle("VAN", VAN)));
        assertEquals(2, parking.enter(vehicle("MOTO", MOTORCYCLE)).id().number());
    }

    @Test
    void capacityCannotBeExceededAndExitAllowsReentryWithNewTicket() {
        var parking = parking(new BestFitAllocation(), hourly(), new TestClock());
        var motorcycle = parking.enter(vehicle("A", MOTORCYCLE));
        parking.enter(vehicle("B", CAR));
        parking.enter(vehicle("C", VAN));
        reject(parking, NO_COMPATIBLE_SPOT, () -> parking.enter(vehicle("D", MOTORCYCLE)));
        parking.exit(motorcycle.id());
        var again = parking.enter(vehicle("A", MOTORCYCLE));
        assertEquals(4, again.id().number());
        assertEquals(3, parking.snapshot().activeTickets().size());
        assertFalse(parking.snapshot().tickets().get(motorcycle.id()).isActive());
    }

    @Test
    void foreignTicketWithSameLocalNumberCannotCloseOwnTicket() {
        var first = parking(new BestFitAllocation(), hourly(), new TestClock());
        var second = parking(new BestFitAllocation(), hourly(), new TestClock());
        var own = first.enter(vehicle("A", CAR));
        var foreign = second.enter(vehicle("A", CAR));
        assertEquals(own.id().number(), foreign.id().number());
        reject(first, UNKNOWN_TICKET, () -> first.exit(foreign.id()));
        assertTrue(first.snapshot().tickets().get(own.id()).isActive());
    }

    @Test
    void repeatedExitIsRejectedAndHistoricalFeeDoesNotChange() {
        var clock = new TestClock();
        var parking = parking(new BestFitAllocation(), hourly(), clock);
        var ticket = parking.enter(vehicle("A", CAR));
        clock.advance(Duration.ofMinutes(11));
        var receipt = parking.exit(ticket.id());
        clock.advance(Duration.ofDays(1));
        reject(parking, TICKET_ALREADY_CLOSED, () -> parking.exit(ticket.id()));
        assertSame(receipt, parking.snapshot().tickets().get(ticket.id()).receipt().orElseThrow());
        assertEquals(new Money(500), receipt.fee());
    }

    @Test
    void clockRollbackUsesLastSuccessfulOperationOfWholeParking() {
        var clock = new TestClock();
        var parking = parking(new BestFitAllocation(), hourly(), clock);
        var first = parking.enter(vehicle("A", CAR));
        clock.advance(Duration.ofMinutes(20));
        parking.enter(vehicle("B", MOTORCYCLE));
        clock.advance(Duration.ofMinutes(-10));
        // Czas nadal jest po wjeździe A, ale przed ostatnią operacją parkingu.
        reject(parking, CLOCK_MOVED_BACKWARDS, () -> parking.exit(first.id()));
        reject(parking, CLOCK_MOVED_BACKWARDS, () -> parking.enter(vehicle("C", VAN)));
        clock.advance(Duration.ofMinutes(10));
        assertEquals(new Money(500), parking.exit(first.id()).fee());
    }

    @Test
    void failedPricingDoesNotCloseTicketOrReleasePlace() {
        var clock = new TestClock();
        FeePolicy broken = context -> {
            throw new IllegalStateException("Awaria wyceny");
        };
        var parking = parking(new BestFitAllocation(), broken, clock);
        var ticket = parking.enter(vehicle("A", CAR));
        var before = parking.snapshot();
        clock.advance(Duration.ofHours(2));
        assertThrows(IllegalStateException.class, () -> parking.exit(ticket.id()));
        assertSame(before, parking.snapshot());
        assertEquals(2, before.availableSpots().size());
        assertTrue(before.tickets().get(ticket.id()).isActive());
    }

    @Test
    void overflowDuringPricingPreservesActiveStay() {
        var clock = new TestClock();
        var fees = new HourlyFeePolicy(new Money(Long.MAX_VALUE), Duration.ZERO);
        var parking = parking(new BestFitAllocation(), fees, clock);
        var ticket = parking.enter(vehicle("A", CAR));
        var before = parking.snapshot();
        clock.advance(Duration.ofHours(2));
        assertThrows(ArithmeticException.class, () -> parking.exit(ticket.id()));
        assertSame(before, parking.snapshot());
    }

    @Test
    void nullFeeIsNotTreatedAsSuccessfulExit() {
        var parking = parking(new BestFitAllocation(), context -> null, new TestClock());
        var ticket = parking.enter(vehicle("A", CAR));
        var before = parking.snapshot();
        assertThrows(NullPointerException.class, () -> parking.exit(ticket.id()));
        assertSame(before, parking.snapshot());
    }

    @Test
    void sourceAndReturnedCollectionsCannotMutateParking() {
        var source = new ArrayList<>(SPOTS);
        var parking = new ParkingLot(source, new BestFitAllocation(), hourly(), new TestClock());
        source.clear();
        parking.enter(vehicle("A", CAR));
        var view = parking.snapshot();
        assertEquals(3, view.spots().size());
        assertThrows(UnsupportedOperationException.class, () -> view.spots().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.tickets().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.activeTickets().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.availableSpots().clear());
    }

    @Test
    void rejectsEmptyParkingAndDuplicateSpotIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new ParkingLot(List.of(), new FirstAvailableAllocation(),
                hourly(), new TestClock()));
        var duplicate = List.of(new ParkingSpot(" A ", 0, SpotType.COMPACT), new ParkingSpot("A", 1, SpotType.LARGE));
        assertThrows(IllegalArgumentException.class, () -> new ParkingLot(duplicate, new FirstAvailableAllocation(),
                hourly(), new TestClock()));
    }

    @Test
    void nullCommandsAreProgrammingErrorsAndDoNotPublishState() {
        var parking = parking(new BestFitAllocation(), hourly(), new TestClock());
        var before = parking.snapshot();
        assertThrows(NullPointerException.class, () -> parking.enter(null));
        assertThrows(NullPointerException.class, () -> parking.exit(null));
        assertSame(before, parking.snapshot());
    }

    @Test
    void duplicateAndClosedTicketReasonsHavePriorityOverClockRollback() {
        var clock = new TestClock();
        var parking = parking(new BestFitAllocation(), hourly(), clock);
        var ticket = parking.enter(vehicle("A", CAR));
        clock.advance(Duration.ofSeconds(-1));
        reject(parking, VEHICLE_ALREADY_PARKED, () -> parking.enter(vehicle("A", CAR)));
        clock.advance(Duration.ofSeconds(1));
        parking.exit(ticket.id());
        clock.advance(Duration.ofSeconds(-1));
        reject(parking, TICKET_ALREADY_CLOSED, () -> parking.exit(ticket.id()));
    }

    @Test
    void activeVehicleQueryIgnoresClosedHistoryAndKeepsOldViewStable() {
        var parking = parking(new BestFitAllocation(), hourly(), new TestClock());
        var plate = new LicensePlate("WX1");
        assertFalse(parking.snapshot().hasActiveVehicle(plate));
        var ticket = parking.enter(vehicle("WX1", CAR));
        var occupied = parking.snapshot();
        assertTrue(occupied.hasActiveVehicle(new LicensePlate(" wx1 ")));
        parking.exit(ticket.id());
        assertFalse(parking.snapshot().hasActiveVehicle(plate));
        assertTrue(occupied.hasActiveVehicle(plate));
        assertEquals(1, parking.snapshot().tickets().size());
        assertThrows(NullPointerException.class, () -> parking.snapshot().hasActiveVehicle(null));
    }

    @Test
    void clockReadFailureDuringEntryPreservesStateAndNumberAndAllowsRetry() {
        var clock = new TestClock();
        var parking = parking(new BestFitAllocation(), hourly(), clock);
        var before = parking.snapshot();
        clock.failReads(true);
        assertThrows(IllegalStateException.class, () -> parking.enter(vehicle("A", CAR)));
        assertSame(before, parking.snapshot());
        clock.failReads(false);
        assertEquals(1, parking.enter(vehicle("A", CAR)).id().number());
    }

    @Test
    void clockReadFailureDuringExitDoesNotReleaseSpotAndAllowsRetry() {
        var clock = new TestClock();
        var parking = parking(new BestFitAllocation(), hourly(), clock);
        var ticket = parking.enter(vehicle("A", CAR));
        var before = parking.snapshot();
        clock.advance(Duration.ofMinutes(20));
        clock.failReads(true);
        assertThrows(IllegalStateException.class, () -> parking.exit(ticket.id()));
        assertSame(before, parking.snapshot());
        assertTrue(parking.snapshot().hasActiveVehicle(ticket.vehicle().plate()));
        clock.failReads(false);
        assertEquals(new Money(500), parking.exit(ticket.id()).fee());
        assertEquals(3, parking.snapshot().availableSpots().size());
    }

    private static void reject(ParkingLot parking, ParkingException.Reason reason, Runnable command) {
        var before = parking.snapshot();
        var error = assertThrows(ParkingException.class, command::run);
        assertEquals(reason, error.reason());
        // Ta sama referencja jest częścią gwarancji odmowy: nic nie opublikowano,
        // nawet jeśli po drodze trzeba było sprawdzić czas lub kandydatów.
        assertSame(before, parking.snapshot());
    }
}

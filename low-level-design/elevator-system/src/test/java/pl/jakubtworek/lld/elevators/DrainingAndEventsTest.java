package pl.jakubtworek.lld.elevators;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.elevators.Fixtures.*;
import static pl.jakubtworek.lld.elevators.ElevatorException.Reason.*;
import static pl.jakubtworek.lld.elevators.ElevatorEvent.*;

/**
 * Sprawdzamy zachowanie przyjętych zobowiązań, końcowe zamknięcie drzwi i fakty tego samego opublikowanego kroku.
 */
class DrainingAndEventsTest {

    @Test
    void drainingFinishesExistingRequestsRejectsNewOnesAndDisablesAfterClosingDoors() {
        var group = group();
        group.call(2);
        group.selectFloor(E1, 4);
        var before = group.snapshot();
        group.beginDraining(E1);
        assertEquals(ServiceMode.IN_SERVICE, before.cars().get(E1).mode());
        assertEquals(ServiceMode.DRAINING, group.snapshot().cars().get(E1).mode());
        assertEquals(List.of(2, 4), group.snapshot().cars().get(E1).stops());
        assertEquals(E1, group.call(2));
        assertEquals(E2, group.call(1));
        reject(group, DRAINING, () -> group.selectFloor(E1, 2));
        reject(group, DRAINING, () -> group.selectFloor(E1, 5));
        reject(group, ALREADY_DRAINING, () -> group.beginDraining(E1));
        group.tick();
        group.tick();
        assertFalse(group.snapshot().hallCalls().containsKey(2));
        group.tick();
        group.tick();
        group.tick();
        assertEquals(DoorState.OPEN, group.snapshot().cars().get(E1).doors());
        assertEquals(ServiceMode.DRAINING, group.snapshot().cars().get(E1).mode());
        var last = group.tick();
        assertEquals(ServiceMode.OUT_OF_SERVICE, last.snapshot().cars().get(E1).mode());
        assertEquals(List.of(new DoorsClosed(E1, 4), new ModeChanged(E1, ServiceMode.DRAINING,
                ServiceMode.OUT_OF_SERVICE), new Moved(E2, 3, 2)), last.events());
        assertSame(group.snapshot(), last.snapshot());
    }

    @Test
    void existingHallCallRemainsValidWhenNoCarAcceptsNewCalls() {
        var group = group();
        group.call(2);
        group.beginDraining(E1);
        group.takeOutOfService(E2);
        var before = group.snapshot();
        assertEquals(E1, group.call(2));
        assertSame(before, group.snapshot());
        reject(group, NO_AVAILABLE_ELEVATOR, () -> group.call(3));
        group.tick();
        group.tick();
        group.tick();
        assertTrue(group.snapshot().hallCalls().isEmpty());
        assertEquals(ServiceMode.OUT_OF_SERVICE, group.snapshot().cars().get(E1).mode());
    }

    @Test
    void idleDrainIsImmediateAndCancellingDrainPreservesProgressAndQueue() {
        var group = group();
        group.beginDraining(E2);
        assertEquals(ServiceMode.OUT_OF_SERVICE, group.snapshot().cars().get(E2).mode());
        reject(group, OUT_OF_SERVICE, () -> group.beginDraining(E2));
        group.selectFloor(E1, 4);
        group.tick();
        group.beginDraining(E1);
        var car = group.snapshot().cars().get(E1);
        group.returnToService(E1);
        var resumed = group.snapshot().cars().get(E1);
        assertEquals(ServiceMode.IN_SERVICE, resumed.mode());
        assertEquals(car.floor(), resumed.floor());
        assertEquals(car.direction(), resumed.direction());
        assertEquals(car.doors(), resumed.doors());
        assertEquals(car.stops(), resumed.stops());
        group.selectFloor(E1, 5);
        assertEquals(List.of(4, 5), group.snapshot().cars().get(E1).stops());
    }

    @Test
    void currentFloorPickupDoesNotEmitMovementAndIdleTickHasNoEvents() {
        var group = group();
        var initial = group.snapshot();
        var idle = group.tick();
        assertSame(initial, idle.snapshot());
        assertTrue(idle.events().isEmpty());
        group.call(0);
        var arrived = group.tick();
        assertEquals(List.of(new DoorsOpened(E1, 0), new HallCallServed(E1, 0)), arrived.events());
        group.beginDraining(E1);
        assertEquals(List.of(new DoorsClosed(E1, 0), new ModeChanged(E1, ServiceMode.DRAINING,
                ServiceMode.OUT_OF_SERVICE)), group.tick().events());
    }

    @Test
    void eventsHaveStableCarOrderAndNoFakeHallCallForCabinDestination() {
        var cars = List.of(new Elevator(E2, BUILDING, 8), new Elevator(E1, BUILDING, 0));
        var group = new ElevatorGroup(BUILDING, cars, new NearestElevator());
        group.call(1);
        group.selectFloor(E2, 7);
        var result = group.tick();
        assertEquals(List.of(new Moved(E1, 0, 1), new DoorsOpened(E1, 1), new HallCallServed(E1, 1), new Moved(E2, 8,
                7), new DoorsOpened(E2, 7)), result.events());
        assertSame(group.snapshot(), result.snapshot());
        assertThrows(UnsupportedOperationException.class, () -> result.events().clear());
        var source = new ArrayList<>(result.events());
        var copy = new TickResult(result.snapshot(), source);
        source.clear();
        group.tick();
        assertEquals(5, copy.events().size());
        assertEquals(DoorState.OPEN, result.snapshot().cars().get(E1).doors());
    }

    @Test
    void invalidCommandsDoNotPublishAndEventValuesRejectImpossibleFacts() {
        var group = group();
        reject(group, UNKNOWN_ELEVATOR, () -> group.beginDraining(new ElevatorId("X")));
        var before = group.snapshot();
        assertThrows(NullPointerException.class, () -> group.beginDraining(null));
        assertSame(before, group.snapshot());
        assertThrows(IllegalArgumentException.class, () -> new Moved(E1, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> new ModeChanged(E1, ServiceMode.IN_SERVICE,
                ServiceMode.IN_SERVICE));
        assertThrows(NullPointerException.class, () -> new TickResult(null, List.of()));
    }

    private static void reject(ElevatorGroup group, ElevatorException.Reason reason, Runnable action) {
        var before = group.snapshot();
        assertEquals(reason, assertThrows(ElevatorException.class, action::run).reason());
        assertSame(before, group.snapshot());
    }
}

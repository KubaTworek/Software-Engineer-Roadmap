package pl.jakubtworek.lld.elevators;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.elevators.Fixtures.*;
import static pl.jakubtworek.lld.elevators.ElevatorException.Reason.*;

/**
 * Scenariusze łączą kabinę, wybór i wezwanie; kontrolują stan po sukcesie oraz tożsamość widoku po odmowie.
 */
class ElevatorGroupTest {

    @Test
    void hallPickupCarDestinationAndDoorCycleFormCompleteTrip() {
        var group = group();
        var initial = group.snapshot();
        assertEquals(E1, group.call(2));
        var assigned = group.snapshot();
        assertEquals(E1, assigned.hallCalls().get(2));
        group.tick();
        assertTrue(group.snapshot().hallCalls().containsKey(2));
        group.tick();
        assertTrue(group.snapshot().hallCalls().isEmpty());
        assertEquals(DoorState.OPEN, group.snapshot().cars().get(E1).doors());
        group.selectFloor(E1, 4);
        group.tick();
        assertEquals(2, group.snapshot().cars().get(E1).floor());
        group.tick();
        group.tick();
        assertEquals(4, group.snapshot().cars().get(E1).floor());
        assertEquals(DoorState.OPEN, group.snapshot().cars().get(E1).doors());
        assertEquals(0, initial.cars().get(E1).floor());
        assertTrue(initial.hallCalls().isEmpty());
        assertEquals(List.of(2), assigned.cars().get(E1).stops());
    }

    @Test
    void activeHallCallIsDeduplicatedAndServedOnlyByAssignedCar() {
        var group = group();
        assertEquals(E1, group.call(3));
        var before = group.snapshot();
        assertEquals(E1, group.call(3));
        assertSame(before, group.snapshot());
        group.selectFloor(E2, 3);
        group.tick();
        group.tick();
        group.tick();
        assertFalse(group.snapshot().hallCalls().containsKey(3));
        group.tick();
        group.tick();
        assertEquals(DoorState.OPEN, group.snapshot().cars().get(E2).doors());
    }

    @Test
    void arrivalOfOtherCarDoesNotClearAssignedCall() {
        var group = group((cars, floor) -> E1);
        group.selectFloor(E1, 10);
        group.call(7);
        group.selectFloor(E2, 7);
        group.tick();
        assertEquals(DoorState.OPEN, group.snapshot().cars().get(E2).doors());
        assertEquals(E1, group.snapshot().hallCalls().get(7));
    }

    @Test
    void hallRequestAtOpenDoorIsImmediateAndClosedDoorRequestWaitsForTick() {
        var group = group();
        group.call(0);
        assertTrue(group.snapshot().hallCalls().containsKey(0));
        group.tick();
        var open = group.snapshot();
        assertEquals(E1, group.call(0));
        assertSame(open, group.snapshot());
        group.tick();
        group.call(0);
        assertEquals(List.of(0), group.snapshot().cars().get(E1).stops());
    }

    @Test
    void hallRequestCanAttachToExistingCabinStopWithoutDuplicateQueueEntry() {
        var group = group();
        group.selectFloor(E1, 2);
        group.call(2);
        assertEquals(List.of(2), group.snapshot().cars().get(E1).stops());
        assertEquals(E1, group.snapshot().hallCalls().get(2));
        group.tick();
        group.tick();
        assertTrue(group.snapshot().hallCalls().isEmpty());
    }

    @Test
    void dispatchPoliciesDifferWhenNearbyCarHasMorePendingStops() {
        var nearest = group(new NearestElevator());
        var least = group(new LeastPendingStops());
        for (var group : List.of(nearest, least)) {
            group.selectFloor(E1, 10);
            group.selectFloor(E1, 9);
        }
        assertEquals(E1, nearest.call(1));
        assertEquals(E2, least.call(1));
        var tie = group();
        assertEquals(E1, tie.call(4));
    }

    @Test
    void dispatchDistanceAcrossFullIntegerRangeUsesLongAndStableTieBreak() {
        var building = new Building(Integer.MIN_VALUE, Integer.MAX_VALUE);
        for (var policy : List.<DispatchPolicy>of(new NearestElevator(), new LeastPendingStops())) {
            var group = new ElevatorGroup(building, List.of(new Elevator(E1, building, Integer.MIN_VALUE),
                    new Elevator(E2, building, Integer.MAX_VALUE)), policy);
            // Od 0 do MIN jest 2147483648 pięter, do MAX o jedno mniej.
            // abs(int MIN) dałoby liczbę ujemną i błędnie wybrało E1.
            assertEquals(E2, group.call(0));
            var tie = new ElevatorGroup(BUILDING, List.of(new Elevator(E2, BUILDING, 8), new Elevator(E1, BUILDING, 0)),
                    policy);
            assertEquals(E1, tie.call(4));
        }
    }

    @Test
    void disabledCarIsExcludedAndRestorationMakesItEligibleAgain() {
        var group = group();
        group.takeOutOfService(E1);
        assertEquals(E2, group.call(0));
        reject(group, OUT_OF_SERVICE, () -> group.selectFloor(E1, 1));
        reject(group, NOT_IDLE, () -> group.takeOutOfService(E2));
        group.returnToService(E1);
        assertEquals(E1, group.call(1));
    }

    @Test
    void allDisabledInvalidFloorsAndUnknownCarsRejectWithoutPublication() {
        var group = group();
        reject(group, INVALID_FLOOR, () -> group.call(11));
        reject(group, INVALID_FLOOR, () -> group.selectFloor(E1, -3));
        reject(group, UNKNOWN_ELEVATOR, () -> group.selectFloor(new ElevatorId("X"), 0));
        reject(group, UNKNOWN_ELEVATOR, () -> group.takeOutOfService(new ElevatorId("X")));
        group.takeOutOfService(E1);
        group.takeOutOfService(E2);
        reject(group, NO_AVAILABLE_ELEVATOR, () -> group.call(0));
        var before = group.snapshot();
        group.tick();
        assertSame(before, group.snapshot());
    }

    @Test
    void failingMutatingNullForeignAndDisabledSelectionsCannotPublish() {
        List<DispatchPolicy> policies = List.of((cars, floor) -> null, (cars, floor) -> new ElevatorId("X"), (cars,
                floor) -> E2, (cars, floor) -> {
            throw new IllegalStateException("Awaria strategii");
        }, (cars, floor) -> {
            cars.clear();
            return E1;
        });
        for (var policy : policies) {
            var group = group(policy);
            group.takeOutOfService(E2);
            var before = group.snapshot();
            assertThrows(RuntimeException.class, () -> group.call(2));
            assertSame(before, group.snapshot());
        }
    }

    @Test
    void inputCollectionsSnapshotsAndDuplicateTargetsCannotMutatePublishedState() {
        var cars = new ArrayList<>(List.of(new Elevator(E1, BUILDING, 0)));
        var group = new ElevatorGroup(BUILDING, cars, new NearestElevator());
        cars.clear();
        group.selectFloor(E1, 1);
        var view = group.snapshot();
        group.selectFloor(E1, 1);
        assertSame(view, group.snapshot());
        assertThrows(UnsupportedOperationException.class, () -> view.cars().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.hallCalls().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.cars().get(E1).stops().clear());
    }

    @Test
    void invalidCatalogAndNullArgumentsFailBeforePublication() {
        assertThrows(IllegalArgumentException.class, () -> new ElevatorGroup(BUILDING, List.of(),
                new NearestElevator()));
        var car = new Elevator(E1, BUILDING, 0);
        assertThrows(IllegalArgumentException.class, () -> new ElevatorGroup(BUILDING, List.of(car, car),
                new NearestElevator()));
        assertThrows(IllegalArgumentException.class, () -> new ElevatorGroup(new Building(0, 10), List.of(car),
                new NearestElevator()));
        assertThrows(IllegalArgumentException.class, () -> new ElevatorGroup(BUILDING, List.of(car.requestStop(1)),
                new NearestElevator()));
        assertThrows(NullPointerException.class, () -> new ElevatorGroup(BUILDING, List.of(car), null));
        var group = group();
        var before = group.snapshot();
        assertThrows(NullPointerException.class, () -> group.selectFloor(null, 1));
        assertThrows(NullPointerException.class, () -> group.takeOutOfService(null));
        assertThrows(NullPointerException.class, () -> group.returnToService(null));
        assertSame(before, group.snapshot());
    }

    private static void reject(ElevatorGroup group, ElevatorException.Reason reason, Runnable action) {
        var before = group.snapshot();
        assertEquals(reason, assertThrows(ElevatorException.class, action::run).reason());
        assertSame(before, group.snapshot());
    }
}

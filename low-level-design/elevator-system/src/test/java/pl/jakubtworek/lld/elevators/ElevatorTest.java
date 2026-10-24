package pl.jakubtworek.lld.elevators;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.elevators.Fixtures.*;
import static pl.jakubtworek.lld.elevators.ElevatorException.Reason.*;

/**
 * Lokalne przejścia testujemy w pakiecie domenowym; publikację zespołu sprawdza osobna klasa.
 */
class ElevatorTest {

    @Test
    void movementArrivalAndDoorClosingAreSeparateTransitions() {
        var original = new Elevator(E1, BUILDING, 0);
        var requested = original.requestStop(2);
        var moving = requested.tick();
        assertEquals(1, moving.floor());
        assertEquals(Direction.UP, moving.direction());
        assertEquals(DoorState.CLOSED, moving.doors());
        var arrived = moving.tick();
        assertEquals(2, arrived.floor());
        assertEquals(Direction.IDLE, arrived.direction());
        assertEquals(DoorState.OPEN, arrived.doors());
        assertTrue(arrived.stops().isEmpty());
        var closed = arrived.tick();
        assertEquals(DoorState.CLOSED, closed.doors());
        assertEquals(2, closed.floor());
        assertSame(closed, closed.tick());
        assertEquals(0, original.floor());
        assertTrue(original.stops().isEmpty());
    }

    @Test
    void fifoCanPassLaterStopWithoutServingItAndReverseAfterFirstTarget() {
        var car = new Elevator(E1, BUILDING, 0).requestStop(3).requestStop(1);
        car = car.tick();
        assertEquals(1, car.floor());
        assertEquals(DoorState.CLOSED, car.doors());
        assertEquals(List.of(3, 1), car.stops());
        car = car.tick().tick();
        assertEquals(3, car.floor());
        assertEquals(List.of(1), car.stops());
        car = car.tick();
        assertEquals(3, car.floor());
        assertEquals(DoorState.CLOSED, car.doors());
        car = car.tick();
        assertEquals(2, car.floor());
        assertEquals(Direction.DOWN, car.direction());
        car = car.tick();
        assertEquals(1, car.floor());
        assertEquals(DoorState.OPEN, car.doors());
    }

    @Test
    void duplicateStopsAndRequestsAtOpenDoorsAreNoOpsButClosedDoorsNeedTick() {
        var car = new Elevator(E1, BUILDING, 0).requestStop(0);
        assertSame(car, car.requestStop(0));
        assertEquals(DoorState.CLOSED, car.doors());
        var open = car.tick();
        assertEquals(DoorState.OPEN, open.doors());
        assertSame(open, open.requestStop(0));
        var withOther = open.requestStop(2);
        assertEquals(DoorState.OPEN, withOther.doors());
        assertEquals(0, withOther.tick().floor());
        assertEquals(1, withOther.tick().tick().floor());
    }

    @Test
    void serviceModeRequiresIdleAndRejectsRepeatedTransitions() {
        var car = new Elevator(E1, BUILDING, 0);
        assertEquals(ALREADY_IN_SERVICE, assertThrows(ElevatorException.class, car::returnToService).reason());
        var queued = car.requestStop(1);
        assertEquals(NOT_IDLE, assertThrows(ElevatorException.class, queued::takeOutOfService).reason());
        var open = queued.tick();
        assertEquals(NOT_IDLE, assertThrows(ElevatorException.class, open::takeOutOfService).reason());
        var disabled = open.tick().takeOutOfService();
        assertEquals(ServiceMode.OUT_OF_SERVICE, disabled.mode());
        assertSame(disabled, disabled.tick());
        assertEquals(OUT_OF_SERVICE, assertThrows(ElevatorException.class, () -> disabled.requestStop(2)).reason());
        assertEquals(ALREADY_OUT_OF_SERVICE, assertThrows(ElevatorException.class, disabled::takeOutOfService)
                .reason());
        assertEquals(ServiceMode.IN_SERVICE, disabled.returnToService().mode());
    }

    @Test
    void extremeFloorsMoveSafelyWithoutSubtractingInts() {
        var building = new Building(Integer.MIN_VALUE, Integer.MAX_VALUE);
        var high = new Elevator(E1, building, Integer.MAX_VALUE).requestStop(Integer.MIN_VALUE).tick();
        assertEquals(Integer.MAX_VALUE - 1, high.floor());
        assertEquals(Direction.DOWN, high.direction());
        var low = new Elevator(E1, building, Integer.MIN_VALUE).requestStop(Integer.MAX_VALUE).tick();
        assertEquals(Integer.MIN_VALUE + 1, low.floor());
        assertEquals(Direction.UP, low.direction());
    }

    @Test
    void valuesRejectInvalidConfigurationAndProtectQueue() {
        assertEquals(E1, new ElevatorId(" e1 "));
        for (String value : List.of("", "E 1", "ą", "x".repeat(33))) {
            assertThrows(IllegalArgumentException.class, () -> new ElevatorId(value));
        }
        assertThrows(NullPointerException.class, () -> new ElevatorId(null));
        assertThrows(IllegalArgumentException.class, () -> new Building(1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Building(2, 1));
        assertEquals(INVALID_FLOOR, assertThrows(ElevatorException.class, () -> new Elevator(E1, BUILDING, 11))
                .reason());
        var car = new Elevator(E1, BUILDING, 0).requestStop(1);
        assertThrows(UnsupportedOperationException.class, () -> car.stops().clear());
    }
}

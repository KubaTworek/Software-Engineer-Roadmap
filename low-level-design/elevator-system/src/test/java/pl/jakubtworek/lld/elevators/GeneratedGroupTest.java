package pl.jakubtworek.lld.elevators;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.elevators.Fixtures.*;
import static pl.jakubtworek.lld.elevators.ElevatorException.Reason.*;

/**
 * 1200 poleceń ze stałymi ziarnami sprawdza zachowanie żądań, przejścia i stare
 * widoki. Wyrocznia nie implementuje ponownie całej kabiny: kontroluje lokalne
 * prawa każdego kroku i skończone opróżnienie kolejek po zatrzymaniu dopływu.
 * To próbka, nie dowód wszystkich przeplotów. Model pozostaje jednowątkowy.
 */
class GeneratedGroupTest {

    private record OldView(ElevatorSnapshot view, List<List<Integer>> queues, List<Integer> floors) {
    }

    @ParameterizedTest
    @ValueSource(longs = { 11, 97, 2026, 65537 })
    void generatedCommandsPreserveRequestsTransitionsAndEventuallyDrain(long seed) {
        var random = new Random(seed);
        var group = group(seed == 11 ? new NearestElevator() : seed == 97 ? new LeastPendingStops() : new FewestPickupSteps());
        var oldViews = new ArrayList<OldView>();
        for (int command = 0; command < 300; command++) {
            var before = group.snapshot();
            var carsBefore = List.of(before.cars().get(E1), before.cars().get(E2));
            oldViews.add(new OldView(before, carsBefore.stream().map(car -> List.copyOf(car.stops())).toList(),
                    carsBefore.stream().map(Elevator::floor).toList()));
            var id = random.nextBoolean() ? E1 : E2;
            var car = before.cars().get(id);
            // -3 i 11 celowo są poza zakresem.
            int floor = random.nextInt(15) - 3;
            switch(random.nextInt(6)) {
                case 0 ->
                    {
                        var expected = floor < -2 || floor > 10 ? INVALID_FLOOR : before.hallCalls()
                                .containsKey(floor) ? null : before.cars().values().stream()
                                .noneMatch(value -> value.mode() == ServiceMode.IN_SERVICE) ? NO_AVAILABLE_ELEVATOR : null;
                        if (expected != null) {
                            reject(group, expected, () -> group.call(floor));
                        } else {
                            var assigned = group.call(floor);
                            var previous = before.hallCalls().get(floor);
                            if (previous != null) {
                                assertEquals(previous, assigned);
                                assertSame(before, group.snapshot());
                            } else {
                                var afterCar = group.snapshot().cars().get(assigned);
                                boolean immediate = afterCar.doors() == DoorState.OPEN && afterCar.floor() == floor;
                                assertEquals(!immediate, group.snapshot().hallCalls().containsKey(floor));
                                if (!immediate) {
                                    assertEquals(assigned, group.snapshot().hallCalls().get(floor));
                                }
                            }
                        }
                    }
                case 1 ->
                    {
                        var expected = floor < -2
                                || floor > 10 ? INVALID_FLOOR : car.mode() == ServiceMode.OUT_OF_SERVICE ? OUT_OF_SERVICE : car.mode() == ServiceMode.DRAINING ? DRAINING : null;
                        if (expected != null) {
                            reject(group, expected, () -> group.selectFloor(id, floor));
                        } else {
                            group.selectFloor(id, floor);
                            var expectedQueue = new ArrayList<>(car.stops());
                            if (!expectedQueue.contains(floor) && !(car.doors() == DoorState.OPEN
                                    && car.floor() == floor)) {
                                expectedQueue.add(floor);
                            }
                            assertEquals(expectedQueue, group.snapshot().cars().get(id).stops());
                        }
                    }
                case 2 ->
                    {
                        var result = group.tick();
                        assertSame(group.snapshot(), result.snapshot());
                        verifyTick(before, result);
                    }
                case 3 ->
                    {
                        var expected = car.mode() == ServiceMode.OUT_OF_SERVICE ? ALREADY_OUT_OF_SERVICE : !car.stops()
                                .isEmpty() || car.doors() != DoorState.CLOSED
                                || car.direction() != Direction.IDLE ? NOT_IDLE : null;
                        if (expected != null) {
                            reject(group, expected, () -> group.takeOutOfService(id));
                        } else {
                            group.takeOutOfService(id);
                            assertEquals(ServiceMode.OUT_OF_SERVICE, group.snapshot().cars().get(id).mode());
                        }
                    }
                case 4 ->
                    {
                        if (car.mode() == ServiceMode.IN_SERVICE) {
                            reject(group, ALREADY_IN_SERVICE, () -> group.returnToService(id));
                        } else {
                            group.returnToService(id);
                            assertEquals(ServiceMode.IN_SERVICE, group.snapshot().cars().get(id).mode());
                        }
                    }
                case 5 ->
                    {
                        var expected = car.mode() == ServiceMode.OUT_OF_SERVICE ? OUT_OF_SERVICE : car.mode() == ServiceMode.DRAINING ? ALREADY_DRAINING : null;
                        if (expected != null) {
                            reject(group, expected, () -> group.beginDraining(id));
                        } else {
                            group.beginDraining(id);
                            var expectedMode = car.stops().isEmpty()
                                    && car.doors() == DoorState.CLOSED ? ServiceMode.OUT_OF_SERVICE : ServiceMode.DRAINING;
                            assertEquals(expectedMode, group.snapshot().cars().get(id).mode());
                            assertEquals(car.stops(), group.snapshot().cars().get(id).stops());
                        }
                    }
                default ->
                    throw new AssertionError();
            }
            verifyInvariants(group.snapshot());
        }
        // Najwyżej 13 unikalnych celów na kabinę. Każdy wymaga najwyżej 12
        // przesunięć i zamknięcia drzwi, a cel bieżącego piętra osobnego otwarcia.
        // 200 kroków przekracza ten limit także przy początkowo otwartych drzwiach.
        for (int tick = 0; tick < 200; tick++) {
            var before = group.snapshot();
            var result = group.tick();
            verifyTick(before, result);
        }
        assertTrue(group.snapshot().hallCalls().isEmpty());
        assertTrue(group.snapshot().cars().values().stream().allMatch(car -> car.stops().isEmpty()
                && car.doors() == DoorState.CLOSED));
        assertTrue(group.snapshot().cars().values().stream().noneMatch(car -> car.mode() == ServiceMode.DRAINING));
        for (var old : oldViews) {
            var cars = List.of(old.view().cars().get(E1), old.view().cars().get(E2));
            assertEquals(old.queues(), cars.stream().map(Elevator::stops).toList());
            assertEquals(old.floors(), cars.stream().map(Elevator::floor).toList());
        }
    }

    private static void verifyInvariants(ElevatorSnapshot view) {
        for (var car : view.cars().values()) {
            assertTrue(car.floor() >= -2 && car.floor() <= 10);
            assertEquals(car.stops().size(), new HashSet<>(car.stops()).size());
            assertTrue(car.stops().stream().allMatch(floor -> floor >= -2 && floor <= 10));
            if (car.doors() == DoorState.OPEN) {
                assertEquals(Direction.IDLE, car.direction());
            }
            if (car.mode() == ServiceMode.OUT_OF_SERVICE) {
                assertTrue(car.stops().isEmpty());
                assertEquals(DoorState.CLOSED, car.doors());
                assertEquals(Direction.IDLE, car.direction());
            }
        }
        view.hallCalls().forEach((floor, id) -> {
            var car = view.cars().get(id);
            assertNotEquals(ServiceMode.OUT_OF_SERVICE, car.mode());
            assertTrue(car.stops().contains(floor));
        });
    }

    private static void verifyTick(ElevatorSnapshot before, TickResult result) {
        var after = result.snapshot();
        for (var old : before.cars().values()) {
            var next = after.cars().get(old.id());
            long distance = Math.abs((long) next.floor() - old.floor());
            assertTrue(distance <= 1);
            if (old.mode() == ServiceMode.OUT_OF_SERVICE) {
                assertSame(old, next);
            } else if (old.doors() == DoorState.OPEN) {
                assertEquals(old.floor(), next.floor());
                assertEquals(DoorState.CLOSED, next.doors());
                assertEquals(old.stops(), next.stops());
            } else if (old.stops().isEmpty()) {
                assertSame(old, next);
            } else {
                int target = old.stops().getFirst();
                if (next.doors() == DoorState.OPEN) {
                    assertEquals(target, next.floor());
                    assertEquals(old.stops().subList(1, old.stops().size()), next.stops());
                } else {
                    assertEquals(1, distance);
                    assertEquals(old.stops(), next.stops());
                    assertTrue(Math.abs((long) target - next.floor()) < Math.abs((long) target - old.floor()));
                }
            }
        }
        before.hallCalls().forEach((floor, id) -> {
            var car = after.cars().get(id);
            boolean served = car.doors() == DoorState.OPEN && car.floor() == floor;
            assertEquals(!served, after.hallCalls().containsKey(floor));
        });
        verifyInvariants(after);
        verifyEvents(before, result);
    }

    /**
     * Odtwarzamy tylko skutki jednego tick z typowanych faktów, bez wywołania
     * tick ani porównywania listy z drugą kopią produkcyjnego generatora zdarzeń.
     * Brakujący ruch, otwarcie, usunięcie wezwania lub zmiana trybu ujawni się
     * jako różnica względem opublikowanego wyniku. To nie pełny event sourcing.
     */
    private static void verifyEvents(ElevatorSnapshot before, TickResult result) {
        var floors = new HashMap<ElevatorId, Integer>();
        var doors = new HashMap<ElevatorId, DoorState>();
        var modes = new HashMap<ElevatorId, ServiceMode>();
        var directions = new HashMap<ElevatorId, Direction>();
        var queues = new HashMap<ElevatorId, ArrayList<Integer>>();
        var calls = new HashMap<>(before.hallCalls());
        before.cars().forEach((id, car) -> {
            floors.put(id, car.floor());
            doors.put(id, car.doors());
            modes.put(id, car.mode());
            directions.put(id, car.direction());
            queues.put(id, new ArrayList<>(car.stops()));
        });
        String previous = "";
        for (var event : result.events()) {
            var id = event.car();
            assertTrue(previous.compareTo(id.value()) <= 0);
            previous = id.value();
            switch(event) {
                case ElevatorEvent.Moved moved ->
                    {
                        assertEquals(floors.get(id).intValue(), moved.from());
                        assertEquals(DoorState.CLOSED, doors.get(id));
                        assertNotEquals(ServiceMode.OUT_OF_SERVICE, modes.get(id));
                        floors.put(id, moved.to());
                        directions.put(id, moved.to() > moved.from() ? Direction.UP : Direction.DOWN);
                    }
                case ElevatorEvent.DoorsOpened opened ->
                    {
                        assertEquals(floors.get(id).intValue(), opened.floor());
                        assertEquals(DoorState.CLOSED, doors.get(id));
                        assertEquals(opened.floor(), queues.get(id).removeFirst().intValue());
                        doors.put(id, DoorState.OPEN);
                        directions.put(id, Direction.IDLE);
                    }
                case ElevatorEvent.DoorsClosed closed ->
                    {
                        assertEquals(floors.get(id).intValue(), closed.floor());
                        assertEquals(DoorState.OPEN, doors.get(id));
                        doors.put(id, DoorState.CLOSED);
                        directions.put(id, Direction.IDLE);
                    }
                case ElevatorEvent.HallCallServed served ->
                    {
                        assertEquals(floors.get(id).intValue(), served.floor());
                        assertEquals(DoorState.OPEN, doors.get(id));
                        assertEquals(id, calls.remove(served.floor()));
                    }
                case ElevatorEvent.ModeChanged changed ->
                    {
                        assertEquals(changed.from(), modes.get(id));
                        assertEquals(DoorState.CLOSED, doors.get(id));
                        assertTrue(queues.get(id).isEmpty());
                        modes.put(id, changed.to());
                    }
            }
        }
        result.snapshot().cars().forEach((id, car) -> {
            assertEquals(floors.get(id).intValue(), car.floor());
            assertEquals(doors.get(id), car.doors());
            assertEquals(modes.get(id), car.mode());
            assertEquals(directions.get(id), car.direction());
            assertEquals(queues.get(id), car.stops());
        });
        assertEquals(calls, result.snapshot().hallCalls());
        if (result.events().isEmpty()) {
            assertSame(before, result.snapshot());
        }
    }

    private static void reject(ElevatorGroup group, ElevatorException.Reason reason, Runnable action) {
        var before = group.snapshot();
        assertEquals(reason, assertThrows(ElevatorException.class, action::run).reason());
        assertSame(before, group.snapshot());
    }
}

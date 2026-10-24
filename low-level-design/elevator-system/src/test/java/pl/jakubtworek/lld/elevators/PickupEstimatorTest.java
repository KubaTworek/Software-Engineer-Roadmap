package pl.jakubtworek.lld.elevators;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.elevators.Fixtures.*;

/**
 * Wzór porównujemy z wykonaniem prawdziwych tick, a nie z drugim sumowaniem dystansów.
 */
class PickupEstimatorTest {

    @Test
    void all5200SmallQueueStatesMatchActualPickupSimulation() {
        var queues = new ArrayList<List<Integer>>();
        enumerate(new ArrayList<>(), queues);
        assertEquals(65, queues.size());
        var building = new Building(0, 3);
        int checked = 0;
        for (int initial = 0; initial <= 3; initial++) {
            for (var queue : queues) {
                var car = new Elevator(E1, building, initial);
                for (int target : queue) {
                    car = car.requestStop(target);
                }
                for (int advance = 0; advance < 5; advance++) {
                    for (int pickup = 0; pickup <= 3; pickup++) {
                        long predicted = PickupEstimator.stepsToPickup(car, pickup);
                        var actual = car.requestStop(pickup);
                        int ticks = 0;
                        while (!(actual.floor() == pickup && actual.doors() == DoorState.OPEN)) {
                            assertTrue(ticks < 100, "Symulacja małego budynku powinna skończyć odbiór");
                            actual = actual.tick();
                            ticks++;
                        }
                        assertEquals(ticks, predicted,
                                "Kolejka=" + car.stops() + " piętro=" + car.floor() + " cel=" + pickup);
                        checked++;
                    }
                    car = car.tick();
                }
            }
        }
        assertEquals(5200, checked);
    }

    @Test
    void fullIntegerRangeIsCalculatedWithoutExecutingBillionsOfTicks() {
        var building = new Building(Integer.MIN_VALUE, Integer.MAX_VALUE);
        var car = new Elevator(E1, building, Integer.MIN_VALUE);
        assertEquals(4294967295L, PickupEstimator.stepsToPickup(car, Integer.MAX_VALUE));
        car = car.requestStop(Integer.MAX_VALUE);
        assertEquals(8589934591L, PickupEstimator.stepsToPickup(car, Integer.MIN_VALUE));
    }

    @Test
    void etaPolicyCanDifferFromDistanceAndPendingStopCount() {
        var eta = group(new FewestPickupSteps());
        eta.selectFloor(E1, 10);
        eta.selectFloor(E1, 9);
        assertEquals(21, eta.estimatePickup(E1, 1));
        assertEquals(7, eta.estimatePickup(E2, 1));
        assertEquals(E2, eta.call(1));
        var least = group(new LeastPendingStops());
        var otherEta = group(new FewestPickupSteps());
        for (var group : List.of(least, otherEta)) {
            group.selectFloor(E2, 7);
        }
        assertEquals(E1, least.call(6));
        assertEquals(E2, otherEta.call(6));
        assertEquals(E1, group(new FewestPickupSteps()).call(4));
    }

    @Test
    void estimateIsReadOnlyAndRejectsCarsNotAcceptingNewPickups() {
        var group = group();
        var before = group.snapshot();
        assertEquals(2, group.estimatePickup(E1, 2));
        assertSame(before, group.snapshot());
        group.selectFloor(E1, 2);
        group.beginDraining(E1);
        assertEquals(ElevatorException.Reason.DRAINING, assertThrows(ElevatorException.class,
                () -> group.estimatePickup(E1, 1)).reason());
        group.takeOutOfService(E2);
        assertEquals(ElevatorException.Reason.OUT_OF_SERVICE, assertThrows(ElevatorException.class,
                () -> group.estimatePickup(E2, 1)).reason());
        assertThrows(NullPointerException.class, () -> PickupEstimator.stepsToPickup(null, 1));
    }

    /**
     * Wszystkie uporządkowane podzbiory czterech pięter, w tym pusta kolejka; bez losowania i powtórzeń.
     */
    private static void enumerate(ArrayList<Integer> prefix, List<List<Integer>> queues) {
        queues.add(List.copyOf(prefix));
        for (int floor = 0; floor <= 3; floor++) {
            if (!prefix.contains(floor)) {
                prefix.add(floor);
                enumerate(prefix, queues);
                prefix.removeLast();
            }
        }
    }
}

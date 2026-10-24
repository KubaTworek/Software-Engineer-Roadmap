package pl.jakubtworek.lld.elevators;

import java.util.Objects;

/**
 * Liczba tick do otwarcia drzwi na piętrze odbioru według BIEŻĄCEGO FIFO.
 * Przejazd d pięter wymaga d kroków, a cel na bieżącym piętrze jednego kroku
 * otwarcia. Między celami doliczamy zamknięcie drzwi. Jeśli cel już występuje
 * w kolejce, kończymy na jego pierwszym obsłużeniu, zamiast dopisywać go drugi raz.
 * To analiza O(S), nie O(odległości): pełny zakres int nie uruchamia miliardów tick.
 */
public final class PickupEstimator {

    private PickupEstimator() {
    }

    public static long stepsToPickup(Elevator car, int pickupFloor) {
        Objects.requireNonNull(car, "car");
        car.building().requireFloor(pickupFloor);
        if (car.mode() != ServiceMode.IN_SERVICE) {
            throw new ElevatorException(car.mode() == ServiceMode.DRAINING ? ElevatorException.Reason.DRAINING : ElevatorException.Reason.OUT_OF_SERVICE, "Estymacja nowego odbioru wymaga windy IN_SERVICE");
        }
        if (car.doors() == DoorState.OPEN && car.floor() == pickupFloor) {
            return 0;
        }
        long steps = car.doors() == DoorState.OPEN ? 1 : 0;
        int position = car.floor();
        for (int target : car.stops()) {
            steps = Math.addExact(steps, Math.max(1L, Math.abs((long) target - position)));
            if (target == pickupFloor) {
                return steps;
            }
            // Zamknięcie po wcześniejszym celu.
            steps = Math.addExact(steps, 1);
            position = target;
        }
        return Math.addExact(steps, Math.max(1L, Math.abs((long) pickupFloor - position)));
    }
}

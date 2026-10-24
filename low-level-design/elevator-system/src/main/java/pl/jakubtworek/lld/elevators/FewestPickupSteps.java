package pl.jakubtworek.lld.elevators;

import java.util.Comparator;
import java.util.List;

/**
 * Minimalizuje liczbę kroków wynikającą z aktualnej kolejki i drzwi; remis
 * rozstrzyga kod windy. Nie obiecuje czasu fizycznego ani globalnej optymalności.
 * Kolejka FIFO jest tu częścią założeń: po zmianie algorytmu jazdy trzeba
 * zmienić również estymator i ponownie porównać go z rzeczywistymi przejściami.
 */
public final class FewestPickupSteps implements DispatchPolicy {

    @Override
    public ElevatorId select(List<Elevator> available, int pickupFloor) {
        return available.stream().min(Comparator.comparingLong((Elevator car) -> PickupEstimator.stepsToPickup(car,
                pickupFloor)).thenComparing(car -> car.id().value())).orElseThrow().id();
    }
}

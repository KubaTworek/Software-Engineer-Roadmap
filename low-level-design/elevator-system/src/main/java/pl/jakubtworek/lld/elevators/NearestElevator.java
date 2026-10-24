package pl.jakubtworek.lld.elevators;

import java.util.Comparator;
import java.util.List;

/**
 * Minimalizuje odległość geometryczną, nie przewidywany czas odbioru: kolejka
 * FIFO i zamykanie drzwi mogą opóźnić bliską windę. Odejmowanie w long chroni
 * porównanie skrajnych pięter int. Remis rozstrzyga kod, nie kolejność mapy.
 */
public final class NearestElevator implements DispatchPolicy {

    @Override
    public ElevatorId select(List<Elevator> available, int pickupFloor) {
        return available.stream()
                .min(Comparator.comparingLong((Elevator car) -> Math.abs((long) car.floor() - pickupFloor))
                .thenComparing(car -> car.id().value())).orElseThrow().id();
    }
}

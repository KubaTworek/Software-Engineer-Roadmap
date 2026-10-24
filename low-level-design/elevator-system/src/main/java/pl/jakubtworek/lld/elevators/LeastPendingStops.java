package pl.jakubtworek.lld.elevators;

import java.util.Comparator;
import java.util.List;

/**
 * Najpierw najmniej oczekujących przystanków, potem odległość i kod.
 * Liczba przystanków NIE oznacza liczby osób ani czasu jazdy. Strategia
 * pokazuje koszt zmiany preferencji, nie obiecuje optymalnej przepustowości.
 */
public final class LeastPendingStops implements DispatchPolicy {

    @Override
    public ElevatorId select(List<Elevator> available, int pickupFloor) {
        return available.stream().min(Comparator.comparingInt((Elevator car) -> car.stops().size())
                .thenComparingLong(car -> Math.abs((long) car.floor() - pickupFloor)).thenComparing(car -> car.id()
                .value())).orElseThrow().id();
    }
}

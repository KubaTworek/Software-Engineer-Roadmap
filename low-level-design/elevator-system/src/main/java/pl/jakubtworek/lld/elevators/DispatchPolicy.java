package pl.jakubtworek.lld.elevators;

import java.util.List;

/**
 * Strategy zmienia przydział między kabinami, nie algorytm jazdy kabiny.
 * Wejście to niepusta, niemutowalna lista wind IN_SERVICE i poprawne piętro.
 * Wynik musi być ID jednej z nich. Implementacja ma być czysta i deterministyczna,
 * bez I/O i ponownego wejścia do zespołu; wyjątek nie publikuje przydziału.
 */
public interface DispatchPolicy {

    ElevatorId select(List<Elevator> available, int pickupFloor);
}

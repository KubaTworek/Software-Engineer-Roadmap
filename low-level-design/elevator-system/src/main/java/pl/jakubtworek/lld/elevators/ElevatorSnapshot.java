package pl.jakubtworek.lld.elevators;

import java.util.Map;

/**
 * Publikacja całego zespołu: kabiny i aktywne przydziały muszą zmienić się razem.
 * Wartości mapy cars i listy przystanków są niemutowalne. Snapshot nie jest
 * historią zdarzeń i nie obiecuje trwałości po restarcie procesu.
 */
public final class ElevatorSnapshot {

    private final Map<ElevatorId, Elevator> cars;
    private final Map<Integer, ElevatorId> hallCalls;

    ElevatorSnapshot(Map<ElevatorId, Elevator> cars, Map<Integer, ElevatorId> hallCalls) {
        this.cars = Map.copyOf(cars);
        this.hallCalls = Map.copyOf(hallCalls);
    }

    public Map<ElevatorId, Elevator> cars() {
        return cars;
    }

    public Map<Integer, ElevatorId> hallCalls() {
        return hallCalls;
    }
}

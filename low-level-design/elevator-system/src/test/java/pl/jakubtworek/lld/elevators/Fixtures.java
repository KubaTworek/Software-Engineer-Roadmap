package pl.jakubtworek.lld.elevators;

import java.util.List;

/**
 * Każdy scenariusz dostaje nowy zespół; wartości kabin można współdzielić, ponieważ są niemutowalne.
 */
public final class Fixtures {

    public static final Building BUILDING = new Building(-2, 10);
    public static final ElevatorId E1 = new ElevatorId("E1");
    public static final ElevatorId E2 = new ElevatorId("E2");

    private Fixtures() {
    }

    public static ElevatorGroup group() {
        return group(new NearestElevator());
    }

    public static ElevatorGroup group(DispatchPolicy policy) {
        return new ElevatorGroup(BUILDING, List.of(new Elevator(E1, BUILDING, 0), new Elevator(E2, BUILDING, 8)),
                policy);
    }
}

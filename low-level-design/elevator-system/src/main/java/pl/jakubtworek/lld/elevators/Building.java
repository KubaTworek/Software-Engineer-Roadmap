package pl.jakubtworek.lld.elevators;

/**
 * Zakres pięter jest wspólnym ograniczeniem wszystkich kabin. Liczby ujemne
 * pozwalają opisać garaż. Nie odejmujemy granic w int: pełny zakres int jest
 * poprawny i jego odległość może przekroczyć Integer.MAX_VALUE.
 */
public record Building(int lowestFloor, int highestFloor) {

    public Building {
        if (lowestFloor >= highestFloor) {
            throw new IllegalArgumentException("Budynek wymaga co najmniej dwóch różnych pięter");
        }
    }

    public void requireFloor(int floor) {
        if (floor < lowestFloor || floor > highestFloor) {
            throw new ElevatorException(ElevatorException.Reason.INVALID_FLOOR, "Piętro poza zakresem budynku");
        }
    }
}

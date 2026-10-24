package pl.jakubtworek.lld.elevators;

import java.util.List;
import java.util.Objects;

/**
 * Stan i zdarzenia należą do tej samej zakończonej publikacji. Pusta lista
 * oznacza bezczynny krok i ten sam snapshot. Zdarzenia nie są automatycznie
 * gromadzone; odbiorca może je zachować, ale nie odtworzy z nich wszystkich
 * komend call/select/drain/in, które leżą poza kontraktem tick.
 */
public record TickResult(ElevatorSnapshot snapshot, List<ElevatorEvent> events) {

    public TickResult {
        Objects.requireNonNull(snapshot, "snapshot");
        events = List.copyOf(events);
    }
}

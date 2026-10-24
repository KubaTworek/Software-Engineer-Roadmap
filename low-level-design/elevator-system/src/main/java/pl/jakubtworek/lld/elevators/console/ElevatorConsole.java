package pl.jakubtworek.lld.elevators.console;

import pl.jakubtworek.lld.elevators.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.Comparator;
import java.util.Objects;

/**
 * Adapter tłumaczy polecenia na operacje grupy; nie symuluje ruchu samodzielnie.
 * step N to N kolejnych operacji, nie jeden atomowy przyrost czasu. Limit 1000
 * chroni interaktywną rozmowę przed przypadkowym bardzo długim poleceniem.
 * EOF nie anuluje celów. Strumienie należą do wywołującego, IOException propaguje.
 */
public final class ElevatorConsole {

    private final ElevatorGroup group;
    private final BufferedReader input;
    private final PrintWriter output;

    public ElevatorConsole(ElevatorGroup group, Reader input, PrintWriter output) {
        this.group = Objects.requireNonNull(group, "group");
        this.input = new BufferedReader(Objects.requireNonNull(input, "input"));
        this.output = Objects.requireNonNull(output, "output");
    }

    public void run() throws IOException {
        output.println("Zespół wind: help, call, select, eta, step, drain, out, in, status, q");
        output.flush();
        String line;
        while ((line = input.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            var parts = line.strip().split("\\s+");
            try {
                if (parts[0].equals("q")) {
                    arity(parts, 1);
                    break;
                }
                switch(parts[0]) {
                    case "help" ->
                        {
                            arity(parts, 1);
                            output.println("call PIĘTRO | select WINDA PIĘTRO | eta WINDA PIĘTRO | step [1..1000] | drain WINDA | out WINDA | in WINDA | status | q");
                        }
                    case "call" ->
                        {
                            arity(parts, 2);
                            output.println("Przydzielono " + group.call(Integer.parseInt(parts[1])).value());
                        }
                    case "select" ->
                        {
                            arity(parts, 3);
                            group.selectFloor(new ElevatorId(parts[1]), Integer.parseInt(parts[2]));
                            output.println("Zapisano cel " + parts[2]);
                        }
                    case "step" ->
                        {
                            if (parts.length != 1 && parts.length != 2) {
                                throw new IllegalArgumentException("step wymaga co najwyżej jednej liczby");
                            }
                            int count = parts.length == 2 ? Integer.parseInt(parts[1]) : 1;
                            if (count < 1 || count > 1000) {
                                throw new IllegalArgumentException("Liczba kroków: 1..1000");
                            }
                            for (int step = 0; step < count; step++) {
                                var result = group.tick();
                                output.println("Krok " + (step + 1));
                                result.events().forEach(this::event);
                            }
                            output.println("Wykonano kroki: " + count);
                        }
                    case "out" ->
                        {
                            arity(parts, 2);
                            group.takeOutOfService(new ElevatorId(parts[1]));
                            output.println("Wyłączono " + parts[1]);
                        }
                    case "eta" ->
                        {
                            arity(parts, 3);
                            output.println("Kroki do odbioru: " + group.estimatePickup(new ElevatorId(parts[1]),
                                    Integer.parseInt(parts[2])));
                        }
                    case "drain" ->
                        {
                            arity(parts, 2);
                            group.beginDraining(new ElevatorId(parts[1]));
                            output.println("Tryb " + parts[1] + ": " + group.snapshot().cars()
                                    .get(new ElevatorId(parts[1])).mode());
                        }
                    case "in" ->
                        {
                            arity(parts, 2);
                            group.returnToService(new ElevatorId(parts[1]));
                            output.println("Włączono " + parts[1]);
                        }
                    case "status" ->
                        {
                            arity(parts, 1);
                            group.snapshot().cars().values().stream().sorted(Comparator.comparing(car -> car.id()
                                    .value())).forEach(car -> output.println(car.id()
                                    .value() + " piętro=" + car.floor() + " kierunek=" + car.direction() + " drzwi=" + car.doors() + " tryb=" + car.mode() + " kolejka=" + car.stops()));
                            var calls = new java.util.TreeMap<Integer, String>();
                            group.snapshot().hallCalls().forEach((floor, id) -> calls.put(floor, id.value()));
                            output.println("Wezwania=" + calls);
                        }
                    default ->
                        throw new IllegalArgumentException("Nieznana komenda");
                }
            } catch (ElevatorException error) {
                output.println("Odmowa " + error.reason() + ": " + error.getMessage());
            } catch (IllegalArgumentException error) {
                output.println("Błąd danych: " + error.getMessage());
            }
            output.flush();
        }
        output.println("Koniec demo");
        output.flush();
    }

    /**
     * Zdarzenie opisuje gotowy krok; adapter nie odtwarza reguł przez porównywanie snapshotów.
     */
    private void event(ElevatorEvent event) {
        String id = event.car().value();
        switch(event) {
            case ElevatorEvent.Moved moved ->
                output.println("Ruch " + id + ": " + moved.from() + " -> " + moved.to());
            case ElevatorEvent.DoorsOpened opened ->
                output.println("Drzwi otwarte " + id + " na " + opened.floor());
            case ElevatorEvent.DoorsClosed closed ->
                output.println("Drzwi zamknięte " + id + " na " + closed.floor());
            case ElevatorEvent.HallCallServed served ->
                output.println("Odebrano wezwanie " + id + " na " + served.floor());
            case ElevatorEvent.ModeChanged changed ->
                output.println("Tryb " + id + ": " + changed.from() + " -> " + changed.to());
        }
    }

    private static void arity(String[] parts, int expected) {
        if (parts.length != expected) {
            throw new IllegalArgumentException("Niepoprawna liczba argumentów");
        }
    }
}

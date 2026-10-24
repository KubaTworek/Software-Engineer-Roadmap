package pl.jakubtworek.lld.elevators.console;

import pl.jakubtworek.lld.elevators.*;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * Składa dwa niezależne egzemplarze kabin i wybraną strategię. Nie używamy
 * zegara ani osobnego wątku: bez polecenia step symulacja nie zmienia stanu.
 * Te same komendy i argument strategii odtwarzają ten sam przebieg demo.
 */
public final class ConsoleMain {

    private ConsoleMain() {
    }

    public static void main(String[] args) throws java.io.IOException {
        var out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        if (args.length > 1 || args.length == 1 && !Set.of("nearest", "least", "eta").contains(args[0])) {
            out.println("Użycie: java -jar elevator-system-1.0.0-SNAPSHOT.jar [nearest|least|eta]");
            return;
        }
        DispatchPolicy policy = args.length == 0 ? new NearestElevator() : switch(args[0]) {
            case "least" ->
                new LeastPendingStops();
            case "eta" ->
                new FewestPickupSteps();
            default ->
                new NearestElevator();
        };
        var building = new Building(-2, 10);
        var cars = List.of(new Elevator(new ElevatorId("E1"), building, 0), new Elevator(new ElevatorId("E2"), building,
                8));
        new ElevatorConsole(new ElevatorGroup(building, cars, policy), new InputStreamReader(System.in,
                StandardCharsets.UTF_8), out).run();
    }
}

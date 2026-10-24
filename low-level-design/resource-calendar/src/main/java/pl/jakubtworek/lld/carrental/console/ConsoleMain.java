package pl.jakubtworek.lld.carrental.console;

import pl.jakubtworek.lld.carrental.*;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Składanie demo: domyślnie rzeczywisty UTC, opcjonalna stała data umożliwia powtarzanie przykładów.
 */
public final class ConsoleMain {

    private ConsoleMain() {
    }

    public static void main(String[] args) throws IOException {
        var output = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        Clock clock;
        try {
            if (args.length > 1) {
                throw new IllegalArgumentException("Za dużo argumentów");
            }
            clock = args.length == 0 ? Clock.systemUTC() : Clock.fixed(LocalDate.parse(args[0])
                    .atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        } catch (IllegalArgumentException | DateTimeException error) {
            output.println("Użycie: java -cp resource-calendar-1.0.0-SNAPSHOT.jar pl.jakubtworek.lld.carrental.console.ConsoleMain [YYYY-MM-DD]");
            return;
        }
        var agency = new RentalAgency(List.of(new Vehicle(new VehicleId("V1"), "Kompakt", VehicleCategory.ECONOMY),
                new Vehicle(new VehicleId("V2"), "SUV", VehicleCategory.SUV)),
                List.of(new Customer(new CustomerId("U1"), "Ala"), new Customer(new CustomerId("U2"), "Bartek")),
                clock);
        output.println("Data demo: " + LocalDate.now(clock));
        new RentalConsole(agency, new InputStreamReader(System.in, StandardCharsets.UTF_8), output).run();
    }
}

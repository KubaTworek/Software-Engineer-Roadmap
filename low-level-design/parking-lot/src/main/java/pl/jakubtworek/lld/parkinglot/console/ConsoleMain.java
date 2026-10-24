package pl.jakubtworek.lld.parkinglot.console;

import pl.jakubtworek.lld.parkinglot.BestFitAllocation;
import pl.jakubtworek.lld.parkinglot.CappedFeePolicy;
import pl.jakubtworek.lld.parkinglot.FeePolicy;
import pl.jakubtworek.lld.parkinglot.FirstAvailableAllocation;
import pl.jakubtworek.lld.parkinglot.FlatFeePolicy;
import pl.jakubtworek.lld.parkinglot.HourlyFeePolicy;
import pl.jakubtworek.lld.parkinglot.Money;
import pl.jakubtworek.lld.parkinglot.ParkingLot;
import pl.jakubtworek.lld.parkinglot.ParkingSpot;
import pl.jakubtworek.lld.parkinglot.SpotAllocationStrategy;
import pl.jakubtworek.lld.parkinglot.SpotType;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Composition root: wybiera współpracowników, łączy domenę z zegarem i konsolą.
 * To tutaj if/switch wybiera strategię na podstawie argumentów. Wjazd i wyjazd
 * nie muszą rozpoznawać nazw implementacji. Dwie niezależne decyzje dają cztery
 * kombinacje bez czterech podklas ParkingLot i bez kontenera DI. Opcjonalny
 * limit jest dekoratorem wybranego cennika, nie kolejną podklasą parkingu.
 */
public final class ConsoleMain {

    private ConsoleMain() {
    }

    public static void main(String[] args) throws IOException {
        var output = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        SpotAllocationStrategy allocation;
        FeePolicy fees;
        try {
            if (args.length > 3) {
                throw new IllegalArgumentException("Za dużo argumentów");
            }
            allocation = switch(args.length >= 1 ? args[0] : "first") {
                case "first" ->
                    new FirstAvailableAllocation();
                case "best-fit" ->
                    new BestFitAllocation();
                default ->
                    throw new IllegalArgumentException("Strategia: first albo best-fit");
            };
            fees = switch(args.length >= 2 ? args[1] : "hourly") {
                case "hourly" ->
                    new HourlyFeePolicy(new Money(500), Duration.ofMinutes(10));
                case "flat" ->
                    new FlatFeePolicy(new Money(1200));
                default ->
                    throw new IllegalArgumentException("Cennik: hourly albo flat");
            };
            if (args.length == 3) {
                fees = new CappedFeePolicy(fees, new Money(Long.parseLong(args[2])));
            }
        } catch (IllegalArgumentException error) {
            output.println(error.getMessage());
            output.println("Użycie: java -jar parking-lot-1.0.0-SNAPSHOT.jar [first|best-fit] [hourly|flat] [LIMIT_GROSZ]");
            return;
        }
        var clock = new DemoClock(Instant.parse("2026-01-01T10:00:00Z"));
        var spots = List.of(new ParkingSpot("L1", 0, SpotType.LARGE), new ParkingSpot("M1", 0, SpotType.MOTORCYCLE),
                new ParkingSpot("C1", 1, SpotType.COMPACT));
        var parking = new ParkingLot(spots, allocation, fees, clock);
        new ParkingConsole(parking, clock, new InputStreamReader(System.in, StandardCharsets.UTF_8), output).run();
    }
}

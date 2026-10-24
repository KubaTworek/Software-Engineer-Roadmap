package pl.jakubtworek.lld.parkinglot.console;

import pl.jakubtworek.lld.parkinglot.LicensePlate;
import pl.jakubtworek.lld.parkinglot.ParkingException;
import pl.jakubtworek.lld.parkinglot.ParkingLot;
import pl.jakubtworek.lld.parkinglot.TicketId;
import pl.jakubtworek.lld.parkinglot.Vehicle;
import pl.jakubtworek.lld.parkinglot.VehicleType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.time.Duration;
import java.time.DateTimeException;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;

/**
 * Adapter tłumaczy tekst na operacje domeny. Nie rezerwuje sam miejsc,
 * nie liczy opłaty i nie modyfikuje biletów. Strumienie są wstrzykiwane,
 * więc test rozmowy nie potrzebuje klawiatury ani podmiany globalnego System.in.
 * Reader/Writer pozostają własnością wywołującego. Błędy odczytu propagujemy;
 * PrintWriter może zapisać błąd wyjścia we fladze, której proste demo nie bada.
 */
public final class ParkingConsole {

    private final ParkingLot parking;
    private final DemoClock clock;
    private final BufferedReader input;
    private final PrintWriter output;

    public ParkingConsole(ParkingLot parking, DemoClock clock, Reader input, PrintWriter output) {
        this.parking = Objects.requireNonNull(parking, "parking");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.input = new BufferedReader(Objects.requireNonNull(input, "input"));
        this.output = Objects.requireNonNull(output, "output");
    }

    public void run() throws IOException {
        output.println("Parking — demo edukacyjne. Wpisz help, aby zobaczyć polecenia.");
        while (true) {
            output.print("> ");
            output.flush();
            String line = input.readLine();
            if (line == null || line.strip().equalsIgnoreCase("q")) {
                output.println("Koniec demo. Aktywne bilety nie są automatycznie zamykane.");
                output.flush();
                return;
            }
            try {
                execute(line.strip().split("\\s+"));
            } catch (ParkingException error) {
                output.println("Odmowa [" + error.reason() + "]: " + error.getMessage());
            } catch (IllegalArgumentException | ArithmeticException | DateTimeException error) {
                // Błędny tekst, konfiguracja wartości i zakres liczbowy to
                // komunikaty demo. Błędu kontraktu strategii (IllegalStateException)
                // nie ukrywamy jako „zła komenda”; wymaga naprawy programu.
                output.println("Niepoprawne polecenie lub wartość: " + error.getMessage());
            }
        }
    }

    private void execute(String[] parts) {
        switch(parts[0].toLowerCase(Locale.ROOT)) {
            case "help" ->
                {
                    requireSize(parts, 1);
                    output.println("status | enter MOTORCYCLE/CAR/VAN REJESTRACJA | exit NUMER | advance MINUTY | q");
                }
            case "status" ->
                {
                    requireSize(parts, 1);
                    output.println("Czas symulacji: " + clock.instant());
                    output.println("Wolne: " + parking.snapshot().availableSpots().stream()
                            .map(spot -> spot.id() + "(" + spot.type() + ")").toList());
                    parking.snapshot().tickets().values().stream().sorted(Comparator.comparingLong(ticket -> ticket.id()
                            .number())).forEach(ticket -> output.println("Bilet " + ticket.id()
                            .number() + ": " + ticket.vehicle().plate().value() + " -> " + ticket.spot()
                            .id() + (ticket.isActive() ? " ACTIVE" : " CLOSED " + ticket.receipt().orElseThrow()
                            .fee())));
                }
            case "enter" ->
                {
                    requireSize(parts, 3);
                    var vehicle = new Vehicle(new LicensePlate(parts[2]), VehicleType.valueOf(parts[1]
                            .toUpperCase(Locale.ROOT)));
                    var ticket = parking.enter(vehicle);
                    output.println("Wydano bilet " + ticket.id().number() + ", miejsce " + ticket.spot().id());
                }
            case "exit" ->
                {
                    requireSize(parts, 2);
                    // W obrębie jednego demo użytkownik podaje krótki numer.
                    // Pełną tożsamość uzupełnia adapter, a domena nadal sprawdza
                    // TicketId złożone z ID parkingu i numeru biletu.
                    var receipt = parking.exit(new TicketId(parking.id(), Long.parseLong(parts[1])));
                    output.println("Zamknięto bilet " + receipt.ticketId().number() + ", należność " + receipt.fee());
                }
            case "advance" ->
                {
                    requireSize(parts, 2);
                    clock.advance(Duration.ofMinutes(Long.parseLong(parts[1])));
                    output.println("Czas symulacji: " + clock.instant());
                }
            default ->
                output.println("Nieznane polecenie. Wpisz help.");
        }
    }

    private static void requireSize(String[] parts, int count) {
        if (parts.length != count) {
            throw new IllegalArgumentException("Niewłaściwa liczba argumentów; użyj help");
        }
    }
}

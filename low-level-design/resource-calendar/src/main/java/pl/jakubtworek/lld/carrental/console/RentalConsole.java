package pl.jakubtworek.lld.carrental.console;

import pl.jakubtworek.lld.carrental.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

/**
 * Adapter interpretuje komendy i pokazuje ID umowy. Nie wyszukuje aktualnej
 * rezerwacji po pojeździe, bo stare polecenie mogłoby trafić w nowego klienta.
 * Daty rzeczywistego odbioru i zwrotu ustala model przez Clock, nie tekst wejścia.
 * Strumienie należą do wywołującego; IOException nie jest zwykłym EOF.
 */
public final class RentalConsole {

    private final RentalAgency agency;
    private final BufferedReader input;
    private final PrintWriter output;

    public RentalConsole(RentalAgency agency, Reader input, PrintWriter output) {
        this.agency = Objects.requireNonNull(agency, "agency");
        this.input = new BufferedReader(Objects.requireNonNull(input, "input"));
        this.output = Objects.requireNonNull(output, "output");
    }

    public void run() throws IOException {
        output.println("Wypożyczalnia: help, status, available, reserve, reschedule, conflict, pickup, return, cancel, service, unservice, q");
        output.flush();
        String line;
        while ((line = input.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.strip().split("\\s+");
            try {
                if (parts[0].equals("q")) {
                    arity(parts, 1);
                    break;
                }
                switch(parts[0]) {
                    case "help" ->
                        {
                            arity(parts, 1);
                            output.println("available OD DO | conflict AUTO OD DO | reserve KLIENT AUTO OD DO | reschedule KLIENT UUID OD DO | pickup/return/cancel KLIENT UUID | service AUTO OD DO POWÓD | unservice UUID | status | q");
                        }
                    case "status" ->
                        {
                            arity(parts, 1);
                            status();
                        }
                    case "available" ->
                        {
                            arity(parts, 3);
                            output.println("Planowo wolne: " + agency.snapshot().availableVehicles(period(parts[1],
                                    parts[2])).stream().map(vehicle -> vehicle.id().value()).toList());
                        }
                    case "reserve" ->
                        {
                            arity(parts, 5);
                            var result = agency.reserve(new CustomerId(parts[1]), new VehicleId(parts[2]),
                                    period(parts[3], parts[4]));
                            output.println("Zarezerwowano: " + result.id().value() + ", cena: " + result.quotedTotal());
                        }
                    case "reschedule" ->
                        {
                            arity(parts, 5);
                            var result = agency.reschedule(new CustomerId(parts[1]), ReservationId.parse(parts[2]),
                                    period(parts[3], parts[4]));
                            output.println("Zmieniono termin: " + result.id()
                                    .value() + ", cena: " + result.quotedTotal());
                        }
                    case "conflict" ->
                        {
                            arity(parts, 4);
                            var result = agency.snapshot().conflict(new VehicleId(parts[1]), period(parts[2],
                                    parts[3]));
                            output.println(result.map(RentalConsole::describe).orElse("Brak konfliktu w planie"));
                        }
                    case "pickup" ->
                        {
                            arity(parts, 3);
                            agency.pickup(new CustomerId(parts[1]), ReservationId.parse(parts[2]));
                            output.println("Wydano pojazd");
                        }
                    case "return" ->
                        {
                            arity(parts, 3);
                            var receipt = agency.returnVehicle(new CustomerId(parts[1]), ReservationId.parse(parts[2]));
                            output.println("Zwrócono pojazd: plan=" + receipt.plannedCharge() + ", dopłata=" + receipt.lateCharge() + ", razem=" + receipt.total());
                        }
                    case "cancel" ->
                        {
                            arity(parts, 3);
                            agency.cancel(new CustomerId(parts[1]), ReservationId.parse(parts[2]));
                            output.println("Anulowano rezerwację");
                        }
                    case "service" ->
                        {
                            if (parts.length < 5) {
                                throw new IllegalArgumentException("Brak danych serwisu");
                            }
                            var block = agency.scheduleMaintenance(new VehicleId(parts[1]), period(parts[2], parts[3]),
                                    String.join(" ", Arrays.copyOfRange(parts, 4, parts.length)));
                            output.println("Zaplanowano serwis: " + block.id().value());
                        }
                    case "unservice" ->
                        {
                            arity(parts, 2);
                            agency.removeMaintenance(MaintenanceId.parse(parts[1]));
                            output.println("Usunięto plan serwisu");
                        }
                    default ->
                        throw new IllegalArgumentException("Nieznana komenda");
                }
            } catch (RentalException error) {
                output.println("Odmowa " + error.reason() + ": " + error.getMessage() + error.conflict()
                        .map(conflict -> "; " + describe(conflict)).orElse(""));
            } catch (IllegalArgumentException | DateTimeException | ArithmeticException error) {
                output.println("Błąd danych: " + error.getMessage());
            }
            output.flush();
        }
        // Nie zwracamy aktywnych aut przy q/EOF: zakończenie rozmowy nie jest
        // fizycznym zwrotem. Każda dotychczasowa komenda była kompletną operacją.
        output.println("Koniec demo");
        output.flush();
    }

    private void status() {
        var view = agency.snapshot();
        view.vehicles().values().stream().sorted(Comparator.comparing(vehicle -> vehicle.id().value()))
                .forEach(vehicle -> output.println(vehicle.id()
                .value() + " " + vehicle.model() + " " + vehicle.category() + " " + vehicle.category()
                .dailyRate() + "/dzień"));
        view.customers().values().stream().sorted(Comparator.comparing(customer -> customer.id().value()))
                .forEach(customer -> output.println(customer.id().value() + " " + customer.name()));
        view.reservations().values().stream().sorted(Comparator.comparing(reservation -> reservation.id().value()))
                .forEach(reservation -> output.println(reservation.id().value() + " " + reservation.customerId()
                .value() + " " + reservation.vehicleId().value() + " " + reservation.period()
                .start() + ".." + reservation.period()
                .end() + " " + reservation.status() + " plan=" + reservation.quotedTotal()));
        view.maintenance().values().stream().sorted(Comparator.comparing(block -> block.id().value()))
                .forEach(block -> output.println("Serwis " + block.id().value() + " " + block.vehicleId()
                .value() + " " + block.period() + " " + block.reason()));
    }

    private static RentalPeriod period(String start, String end) {
        return new RentalPeriod(LocalDate.parse(start), LocalDate.parse(end));
    }

    private static String describe(CalendarConflict conflict) {
        return conflict.reason() + " źródło=" + conflict.sourceId() + " auto=" + conflict.vehicleId()
                .value() + " okres=[" + conflict.period().start() + "," + conflict.period().end() + ")";
    }

    private static void arity(String[] parts, int expected) {
        if (parts.length != expected) {
            throw new IllegalArgumentException("Niepoprawna liczba argumentów");
        }
    }
}

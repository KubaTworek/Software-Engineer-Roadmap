package pl.jakubtworek.lld.meetingrooms.console;

import pl.jakubtworek.lld.meetingrooms.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Konsola tłumaczy tekst na wartości i prezentuje wyniki. Nie rozstrzyga
 * kolizji ani praw do anulowania. Offset w dacie jest obowiązkowy; nie zgadujemy
 * strefy z systemu. IOException propaguje, EOF kończy rozmowę bez cofania
 * rezerwacji. Strumienie należą do wywołującego i nie są tutaj zamykane.
 */
public final class BookingConsole {

    private final BookingService service;
    private final BufferedReader input;
    private final PrintWriter output;

    public BookingConsole(BookingService service, Reader input, PrintWriter output) {
        this.service = Objects.requireNonNull(service, "service");
        this.input = new BufferedReader(Objects.requireNonNull(input, "input"));
        this.output = Objects.requireNonNull(output, "output");
    }

    public void run() throws IOException {
        output.println("Rezerwacje sal: help, rooms, available, explain, book, reschedule, cancel, bookings, q");
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
                            output.println("available/explain OSOBY SPRZĘT OD DO | book ORGANIZATOR OSOBY SPRZĘT OD DO TEMAT | reschedule ORGANIZATOR UUID OD DO | cancel ORGANIZATOR UUID | rooms | bookings | q; SPRZĘT: - lub PROJECTOR,WHITEBOARD,VIDEO_CONFERENCE; daty ISO z offsetem");
                        }
                    case "rooms" ->
                        {
                            arity(parts, 1);
                            service.snapshot().rooms().values().stream().sorted(Comparator.comparing(room -> room.id()
                                    .value())).forEach(this::room);
                        }
                    case "available" ->
                        {
                            arity(parts, 5);
                            var rooms = service.availableRooms(requirements(parts[1], parts[2]), slot(parts[3],
                                    parts[4]));
                            if (rooms.isEmpty()) {
                                output.println("Brak dostępnych sal");
                            }
                            rooms.forEach(this::room);
                        }
                    case "book" ->
                        {
                            arity(parts, 7);
                            var booking = service.book(new UserId(parts[1]), requirements(parts[2], parts[3]),
                                    slot(parts[4], parts[5]), parts[6]);
                            output.println("Rezerwacja " + booking.id().value() + " sala=" + booking.roomId().value());
                        }
                    case "explain" ->
                        {
                            arity(parts, 5);
                            report(service.analyzeAvailability(requirements(parts[1], parts[2]), slot(parts[3],
                                    parts[4])));
                        }
                    case "reschedule" ->
                        {
                            arity(parts, 5);
                            var moved = service.reschedule(BookingId.parse(parts[2]), new UserId(parts[1]),
                                    slot(parts[3], parts[4]));
                            output.println("Zmieniono termin " + moved.id().value() + " sala=" + moved.roomId()
                                    .value() + " " + moved.slot().start() + " / " + moved.slot().end());
                        }
                    case "cancel" ->
                        {
                            arity(parts, 3);
                            var cancelled = service.cancel(BookingId.parse(parts[2]), new UserId(parts[1]));
                            output.println("Anulowano " + cancelled.id().value());
                        }
                    case "bookings" ->
                        {
                            arity(parts, 1);
                            service.snapshot().bookings().values().stream()
                                    .sorted(Comparator.comparing((Booking booking) -> booking.slot().start())
                                    .thenComparing(booking -> booking.roomId().value())
                                    .thenComparing(booking -> booking.id().value()))
                                    .forEach(booking -> output.println(booking.id()
                                    .value() + " " + booking.status() + " sala=" + booking.roomId()
                                    .value() + " organizator=" + booking.organizer().value() + " " + booking.slot()
                                    .start() + " / " + booking.slot().end() + " blokada-do=" + service.buffer()
                                    .blockingSlot(booking.slot()).end() + " " + booking.subject()));
                        }
                    default ->
                        throw new IllegalArgumentException("Nieznana komenda");
                }
            } catch (BookingException error) {
                output.println("Odmowa " + error.reason() + ": " + error.getMessage());
                error.availability().ifPresent(this::report);
            } catch (IllegalArgumentException | java.time.DateTimeException | ArithmeticException error) {
                output.println("Błąd danych: " + error.getMessage());
            }
            output.flush();
        }
        output.println("Koniec demo");
        output.flush();
    }

    /**
     * Prezentacja odczytuje gotowe przyczyny; nie oblicza drugiej wersji reguł dostępności.
     */
    private void report(AvailabilityReport report) {
        output.println("Analiza: spotkanie=" + report.requestedSlot().start() + " / " + report.requestedSlot()
                .end() + " blokada-do=" + report.blockingSlot().end());
        for (var assessment : report.rooms()) {
            output.println(assessment.room().id()
                    .value() + " dostępna=" + assessment.available() + " za-mała=" + assessment.insufficientCapacity() + " brak-sprzętu=" + assessment.missingEquipment().stream().map(Enum::name).sorted().toList() + " konflikty=" + assessment.conflicts().stream().map(id -> id.value().toString()).toList());
        }
    }

    private void room(MeetingRoom room) {
        String equipment = room.equipment().stream().map(Enum::name).sorted().collect(Collectors.joining(","));
        output.println(room.id()
                .value() + " " + room.name() + " pojemność=" + room.capacity() + " sprzęt=" + equipment);
    }

    private static RoomRequirements requirements(String count, String equipment) {
        Set<Equipment> items = equipment.equals("-") ? Set.of() : Arrays.stream(equipment.split(",", -1))
                .map(item -> Equipment.valueOf(item.toUpperCase(Locale.ROOT))).collect(Collectors.toUnmodifiableSet());
        return new RoomRequirements(Integer.parseInt(count), items);
    }

    private static TimeSlot slot(String from, String to) {
        return new TimeSlot(OffsetDateTime.parse(from).toInstant(), OffsetDateTime.parse(to).toInstant());
    }

    private static void arity(String[] parts, int expected) {
        if (parts.length != expected) {
            throw new IllegalArgumentException("Niepoprawna liczba argumentów");
        }
    }
}

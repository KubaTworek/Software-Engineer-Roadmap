package pl.jakubtworek.lld.meetingrooms.console;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.meetingrooms.Fixtures.*;
import pl.jakubtworek.lld.meetingrooms.*;

/**
 * Testujemy rozmowę przez wstrzyknięte strumienie, bez modyfikowania System.in i System.out.
 */
class BookingConsoleTest {

    @Test
    void rescheduleAndAvailabilityExplanationExposeBufferAndKeepOldSlotAfterConflict() throws IOException {
        var service = new BookingService(ROOMS, new MutableClock(), new SmallestCapacity(),
                new TurnaroundBuffer(java.time.Duration.ofMinutes(15)));
        var booking = service.book(ALA, people(20), slot(1, 2), "A");
        var blocker = service.book(BARTEK, people(20), slot(3, 4), "B");
        var output = new StringWriter();
        String id = booking.id().value().toString();
        run(service, "explain 99 PROJECTOR 2030-01-01T11:00Z 2030-01-01T12:00Z\n" + "reschedule ALA " + id + " 2030-01-01T11:00Z 2030-01-01T12:00Z\n" + "reschedule ALA " + id + " 2030-01-01T13:00Z 2030-01-01T14:00Z\nbookings\nq\n", output);
        assertTrue(output.toString().contains("za-mała=true"));
        assertTrue(output.toString().contains(blocker.id().value().toString()));
        assertTrue(output.toString().contains("NO_AVAILABLE_ROOM"));
        assertTrue(output.toString().contains("Zmieniono termin " + id));
        assertTrue(output.toString().contains("blokada-do=2030-01-01T14:15:00Z"));
        assertEquals(slot(5, 6), service.snapshot().bookings().get(booking.id()).slot());
    }

    @Test
    void bookingOffsetsConflictAndCancellationUseDomainRules() throws IOException {
        var service = service(new MutableClock());
        var output = new StringWriter();
        run(service, "book ala 20 PROJECTOR 2030-01-01T10:00+01:00 2030-01-01T11:00+01:00 Plan\n" + "book bartek 20 PROJECTOR 2030-01-01T09:00Z 2030-01-01T10:00Z Konflikt\nrooms\nbookings\nq\n", output);
        assertTrue(output.toString().contains("sala=A1"));
        assertTrue(output.toString().contains("NO_AVAILABLE_ROOM"));
        assertEquals(1, service.snapshot().bookings().size());
        var id = service.snapshot().bookings().keySet().iterator().next();
        run(service, "cancel bartek " + id.value() + "\ncancel ala " + id.value() + "\nbookings\nq\n", output);
        assertTrue(output.toString().contains("NOT_ORGANIZER"));
        assertTrue(output.toString().contains("CANCELLED"));
    }

    @Test
    void malformedInputWithoutOffsetAndInvalidEquipmentLeaveSnapshotUntouched() throws IOException {
        var service = service(new MutableClock());
        var before = service.snapshot();
        var output = new StringWriter();
        run(service, "?\nbook\nbook ALA 0 - 2030-01-01T09:00Z 2030-01-01T10:00Z A\n" + "book ALA 2 - 2030-01-01T09:00 2030-01-01T10:00 A\n" + "book ALA 2 TV 2030-01-01T09:00Z 2030-01-01T10:00Z A\ncancel ALA bad-id\nhelp\nq\n", output);
        assertSame(before, service.snapshot());
        assertTrue(output.toString().contains("Błąd danych"));
    }

    @Test
    void availabilityListsCompatibleRoomsOrExplainsEmptyResult() throws IOException {
        var service = service(new MutableClock());
        var output = new StringWriter();
        run(service, "available 4 projector,whiteboard 2030-01-01T09:00Z 2030-01-01T10:00Z\n" + "available 99 - 2030-01-01T09:00Z 2030-01-01T10:00Z\nq\n", output);
        assertTrue(output.toString().contains("A1 Duża"));
        assertTrue(output.toString().contains("C1 Średnia"));
        assertFalse(output.toString().contains("B1 Mała"));
        assertTrue(output.toString().contains("Brak dostępnych sal"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "q\n" })
    void eofAndQuitKeepAcceptedBookings(String ending) throws IOException {
        var service = service(new MutableClock());
        run(service, "book ALA 2 - 2030-01-01T09:00Z 2030-01-01T10:00Z A\n" + ending, new StringWriter());
        assertEquals(1, service.snapshot().bookings().size());
    }

    @Test
    void inputFailurePropagatesWithoutCompensatingExistingBooking() {
        var service = service(new MutableClock());
        service.book(ALA, new RoomRequirements(1, Set.of()), slot(1, 2), "A");
        var before = service.snapshot();
        Reader broken = new Reader() {

            @Override
            public int read(char[] buffer, int offset, int length) throws IOException {
                throw new IOException("Awaria wejścia");
            }

            @Override
            public void close() {
                fail("Adapter nie jest właścicielem zasobu");
            }
        };
        assertThrows(IOException.class, () -> new BookingConsole(service, broken, new PrintWriter(new StringWriter()))
                .run());
        assertSame(before, service.snapshot());
    }

    private static void run(BookingService service, String commands, StringWriter output) throws IOException {
        new BookingConsole(service, new StringReader(commands), new PrintWriter(output)).run();
    }
}

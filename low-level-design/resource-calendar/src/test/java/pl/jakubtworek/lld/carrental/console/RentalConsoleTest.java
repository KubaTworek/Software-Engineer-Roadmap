package pl.jakubtworek.lld.carrental.console;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.jakubtworek.lld.carrental.ReservationStatus;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.carrental.Fixtures.*;

/**
 * Rozmowy adaptera sprawdzamy na strumieniach w pamięci, bez globalnego terminala.
 */
class RentalConsoleTest {

    @Test
    void rescheduleAndConflictCommandsExposeSourceTokensAndPreserveOldTermOnRefusal() throws IOException {
        var agency = agency(new TestClock());
        var own = agency.reserve(U1, V1, period(1, 3));
        var other = agency.reserve(U2, V1, period(4, 6));
        var block = agency.scheduleMaintenance(V1, period(6, 8), "Przegląd");
        var output = new StringWriter();
        new RentalConsole(agency, new StringReader("reschedule U1 " + own.id()
                .value() + " 2026-10-21 2026-10-24\n" + "reschedule U1 " + own.id()
                .value() + " 2026-10-19 2026-10-23\n" + "conflict V1 2026-10-25 2026-10-26\nconflict V2 2026-10-19 2026-10-20\nq\n"), new PrintWriter(output)).run();
        assertTrue(output.toString().contains("źródło=" + other.id().value()));
        assertTrue(output.toString().contains("okres=[2026-10-23,2026-10-25)"));
        assertTrue(output.toString().contains("Zmieniono termin: " + own.id().value() + ", cena: 400.00 PLN"));
        assertTrue(output.toString().contains("źródło=" + block.id().value()));
        assertTrue(output.toString().contains("Brak konfliktu w planie"));
        assertEquals(period(0, 4), agency.snapshot().reservations().get(own.id()).period());
    }

    @Test
    void completeConversationUsesExplicitReservationId() throws IOException {
        var agency = agency(new TestClock());
        var output = new StringWriter();
        new RentalConsole(agency, new StringReader("reserve U1 V1 2026-10-19 2026-10-22\nstatus\n"),
                new PrintWriter(output)).run();
        var id = agency.snapshot().reservations().keySet().iterator().next();
        new RentalConsole(agency,
                new StringReader("pickup U1 " + id.value() + "\nreturn U1 " + id.value() + "\navailable 2026-10-19 2026-10-22\nq\n"), new PrintWriter(output)).run();
        assertTrue(output.toString().contains("Wydano pojazd"));
        assertTrue(output.toString().contains("razem=300.00 PLN"));
        assertTrue(output.toString().contains("Planowo wolne: [V1, V2]"));
        assertEquals(ReservationStatus.COMPLETED, agency.snapshot().reservations().get(id).status());
    }

    @Test
    void serviceConflictRemovalAndCancellationAreExposedByAdapter() throws IOException {
        var agency = agency(new TestClock());
        var output = new StringWriter();
        new RentalConsole(agency,
                new StringReader("service V1 2026-10-19 2026-10-22 Przegląd techniczny\nreserve U1 V1 2026-10-19 2026-10-20\n"), new PrintWriter(output)).run();
        var block = agency.snapshot().maintenance().keySet().iterator().next();
        new RentalConsole(agency,
                new StringReader("unservice " + block.value() + "\nreserve U1 V1 2026-10-19 2026-10-20\n"),
                new PrintWriter(output)).run();
        var id = agency.snapshot().reservations().keySet().iterator().next();
        new RentalConsole(agency, new StringReader("cancel U1 " + id.value() + "\nq\n"), new PrintWriter(output)).run();
        assertTrue(output.toString().contains("MAINTENANCE_CONFLICT"));
        assertEquals(ReservationStatus.CANCELLED, agency.snapshot().reservations().get(id).status());
    }

    @Test
    void invalidTextDatesIdsAndUnknownVehicleDoNotChangeState() throws IOException {
        var agency = agency(new TestClock());
        var before = agency.snapshot();
        var output = new StringWriter();
        new RentalConsole(agency,
                new StringReader("?\nreserve U1\npickup U1 invalid\navailable bad date\nreserve U1 X 2026-10-19 2026-10-20\nhelp\nq\n"), new PrintWriter(output)).run();
        assertTrue(output.toString().contains("Błąd danych"));
        assertTrue(output.toString().contains("UNKNOWN_VEHICLE"));
        assertSame(before, agency.snapshot());
    }

    @ParameterizedTest
    @ValueSource(strings = { "q\n", "" })
    void quitAndEofDoNotReturnActiveVehicle(String ending) throws IOException {
        var agency = agency(new TestClock());
        var reservation = agency.reserve(U1, V1, period(0, 1));
        agency.pickup(U1, reservation.id());
        new RentalConsole(agency, new StringReader(ending), new PrintWriter(new StringWriter())).run();
        assertEquals(ReservationStatus.ACTIVE, agency.snapshot().reservations().get(reservation.id()).status());
    }

    @Test
    void readFailurePropagatesWithoutChangingState() {
        var agency = agency(new TestClock());
        var before = agency.snapshot();
        Reader broken = new Reader() {

            @Override
            public int read(char[] buffer, int offset, int length) throws IOException {
                throw new IOException("Awaria wejścia");
            }

            @Override
            public void close() {
                fail("Strumień należy do wywołującego");
            }
        };
        assertThrows(IOException.class, () -> new RentalConsole(agency, broken, new PrintWriter(new StringWriter()))
                .run());
        assertSame(before, agency.snapshot());
    }
}

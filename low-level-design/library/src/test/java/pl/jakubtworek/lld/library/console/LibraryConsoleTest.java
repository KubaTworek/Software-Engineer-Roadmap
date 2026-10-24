package pl.jakubtworek.lld.library.console;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.library.Fixtures.*;

/**
 * Rozmowy używają publicznego API i strumieni w pamięci; nie testują terminala OS.
 */
class LibraryConsoleTest {

    @Test
    void renewalShowsStableIdentifierAndRejectsSecondRenewal() throws IOException {
        var library = library(1);
        var loan = library.borrow(M1, B1);
        var output = new StringWriter();
        var command = "renew M1 C1 " + loan.id().value() + "\n";
        new LibraryConsole(library, new StringReader(command + command + "status\nq\n"), new PrintWriter(output)).run();
        assertTrue(output.toString().contains("Przedłużono: C1, loan=" + loan.id().value()));
        assertTrue(output.toString().contains("2026-11-16T12:00:00Z"));
        assertTrue(output.toString().contains("ALREADY_RENEWED"));
    }

    @Test
    void oldReturnTokenIsRejectedAndCurrentTokenStillWorks() throws IOException {
        var library = library(1);
        var old = library.borrow(M1, B1);
        library.returnCopy(M1, old.copyId(), old.id());
        var current = library.borrow(M1, B1);
        var output = new StringWriter();
        new LibraryConsole(library, new StringReader("return M1 C1 " + old.id()
                .value() + "\nreturn M1 C1 " + current.id().value() + "\nq\n"), new PrintWriter(output)).run();
        assertTrue(output.toString().contains("STALE_LOAN"));
        assertTrue(output.toString().contains("Zwrócono: C1"));
        assertTrue(library.snapshot().loans().isEmpty());
    }

    @Test
    void reservationRefusalCancellationBorrowAndReturnFormCompleteConversation() throws IOException {
        var library = library(1);
        var output = new StringWriter();
        new LibraryConsole(library,
                new StringReader("reserve M1 B1\nborrow M2 B1\ncancel M1 B1\nborrow M2 B1\nstatus\n"),
                new PrintWriter(output)).run();
        var id = library.snapshot().loans().get(new pl.jakubtworek.lld.library.CopyId("C1")).id().value();
        new LibraryConsole(library, new StringReader("return M2 C1 " + id + "\nq\n"), new PrintWriter(output)).run();
        String text = output.toString();
        assertTrue(text.contains("RESERVATION_PRIORITY"));
        assertTrue(text.contains("Anulowano rezerwację"));
        assertTrue(text.contains("Wypożyczono: C1"));
        assertTrue(text.contains("2026-11-02T12:00:00Z"));
        assertTrue(text.contains("C1 -> M2"));
        assertTrue(text.contains("Zwrócono: C1"));
        assertTrue(library.snapshot().loans().isEmpty());
    }

    @Test
    void invalidCommandsAndReferencesLeaveStateUnchangedAndConversationContinues() throws IOException {
        var library = library(1);
        var before = library.snapshot();
        var output = new StringWriter();
        new LibraryConsole(library,
                new StringReader("???\nborrow M1\nborrow M1 !\nreturn M1 X 00000000-0000-0000-0000-000000000001\nq extra\nhelp\nstatus\nq\n"), new PrintWriter(output)).run();
        assertTrue(output.toString().contains("Błąd danych"));
        assertTrue(output.toString().contains("UNKNOWN_COPY"));
        assertTrue(output.toString().contains("kolejka=[]"));
        assertSame(before, library.snapshot());
    }

    @ParameterizedTest
    @ValueSource(strings = { "q\n", "" })
    void quitAndEofDoNotReturnAlreadyBorrowedBooks(String ending) throws IOException {
        var library = library(1);
        new LibraryConsole(library, new StringReader("borrow M1 B1\n" + ending), new PrintWriter(new StringWriter()))
                .run();
        assertEquals(1, library.snapshot().loans().size());
    }

    @Test
    void readFailurePropagatesWithoutChangingExistingLoans() {
        var library = library(1);
        library.borrow(M1, B1);
        var before = library.snapshot();
        Reader broken = new Reader() {

            @Override
            public int read(char[] buffer, int offset, int length) throws IOException {
                throw new IOException("Awaria wejścia");
            }

            @Override
            public void close() {
                fail("Adapter nie posiada strumienia");
            }
        };
        assertThrows(IOException.class, () -> new LibraryConsole(library, broken, new PrintWriter(new StringWriter()))
                .run());
        assertSame(before, library.snapshot());
    }
}

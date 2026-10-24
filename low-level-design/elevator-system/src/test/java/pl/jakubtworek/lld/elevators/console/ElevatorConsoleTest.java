package pl.jakubtworek.lld.elevators.console;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.elevators.Fixtures.*;
import pl.jakubtworek.lld.elevators.*;

/**
 * Rozmowy korzystają z prawdziwej domeny i strumieni w pamięci, bez globalnego terminala.
 */
class ElevatorConsoleTest {

    @Test
    void estimateDrainAndTypedTickEventsExplainProgress() throws IOException {
        var group = group(new FewestPickupSteps());
        var output = new StringWriter();
        run(group, "call 2\neta E1 2\ndrain E1\nselect E1 3\nstep 3\nstatus\nin E1\nq\n", output);
        assertTrue(output.toString().contains("Kroki do odbioru: 2"));
        assertTrue(output.toString().contains("Odmowa DRAINING"));
        assertTrue(output.toString().contains("Ruch E1: 0 -> 1"));
        assertTrue(output.toString().contains("Odebrano wezwanie E1 na 2"));
        assertTrue(output.toString().contains("DRAINING -> OUT_OF_SERVICE"));
        assertTrue(group.snapshot().hallCalls().isEmpty());
        assertEquals(ServiceMode.IN_SERVICE, group.snapshot().cars().get(E1).mode());
    }

    @Test
    void pickupDestinationAndMaintenanceAreVisibleInConversation() throws IOException {
        var group = group();
        var output = new StringWriter();
        run(group, "call 2\nstep 2\nselect e1 4\nstep 3\nstatus\nout E1\nstep\nout E1\nstatus\nin E1\nq\n", output);
        assertTrue(output.toString().contains("Przydzielono E1"));
        assertTrue(output.toString().contains("piętro=4 kierunek=IDLE drzwi=OPEN"));
        assertTrue(output.toString().contains("NOT_IDLE"));
        assertTrue(output.toString().contains("tryb=OUT_OF_SERVICE"));
        assertEquals(ServiceMode.IN_SERVICE, group.snapshot().cars().get(E1).mode());
        assertTrue(group.snapshot().hallCalls().isEmpty());
    }

    @Test
    void invalidInputAndDomainRefusalsDoNotPreventFurtherCommands() throws IOException {
        var group = group();
        var before = group.snapshot();
        var output = new StringWriter();
        run(group, "?\ncall\ncall x\ncall 99\nselect UNKNOWN 1\nstep 0\nstep 1001\nstep 2 extra\nhelp\nq\n", output);
        assertTrue(output.toString().contains("Błąd danych"));
        assertTrue(output.toString().contains("INVALID_FLOOR"));
        assertTrue(output.toString().contains("UNKNOWN_ELEVATOR"));
        assertSame(before, group.snapshot());
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "q\n" })
    void quitAndEofDoNotUndoPendingCalls(String ending) throws IOException {
        var group = group();
        run(group, "call 2\n" + ending, new StringWriter());
        assertEquals(E1, group.snapshot().hallCalls().get(2));
    }

    @Test
    void readFailurePropagatesWithoutClearingAlreadyAcceptedRequests() {
        var group = group();
        group.call(2);
        var before = group.snapshot();
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
        assertThrows(IOException.class, () -> new ElevatorConsole(group, broken, new PrintWriter(new StringWriter()))
                .run());
        assertSame(before, group.snapshot());
    }

    private static void run(ElevatorGroup group, String commands, StringWriter output) throws IOException {
        new ElevatorConsole(group, new StringReader(commands), new PrintWriter(output)).run();
    }
}

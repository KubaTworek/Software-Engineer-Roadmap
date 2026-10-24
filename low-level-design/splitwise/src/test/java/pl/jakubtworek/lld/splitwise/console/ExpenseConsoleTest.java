package pl.jakubtworek.lld.splitwise.console;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.splitwise.Fixtures.*;

/**
 * Adapter operuje wyłącznie publicznym API, a test nie przejmuje globalnych strumieni.
 */
class ExpenseConsoleTest {

    @Test
    void keyedCommandsRetryAndCorrectionRemainVisibleWithoutDuplicateEntries() throws IOException {
        var group = group();
        var output = new StringWriter();
        new ExpenseConsole(group,
                new StringReader("exact-key cost U1 100 A U2=100\nexact-key cost U1 100 A U2=100\nexact-key cost U1 101 A U2=101\nsettle-key paid U2 U1 100\nsettle-key paid U2 U1 100\nq\n"), new PrintWriter(output)).run();
        assertTrue(output.toString().contains("IDEMPOTENCY_CONFLICT"));
        assertEquals(2, group.snapshot().entries().size());
        var expense = group.snapshot().entries().getFirst();
        String command = "reverse undo U1 " + expense.id().value() + " Pomyłka\n";
        new ExpenseConsole(group, new StringReader(command + command + "history\nq\n"), new PrintWriter(output)).run();
        assertEquals(3, group.snapshot().entries().size());
        assertEquals(-100, group.snapshot().balanceOf(U1).grosz());
        assertTrue(output.toString().contains("Korekta"));
    }

    @Test
    void keyedEqualPercentageAndExactSyntaxUseSameDomainContract() throws IOException {
        var group = group();
        var output = new StringWriter();
        new ExpenseConsole(group,
                new StringReader("equal-key a U1 100 A U1 U2\npercent-key a U1 100 A U2=5000 U1=5000\nexact U1 100 B U1=20 U2=80\nexact U1 100 C U1=50 u1=50\nexact U1 100 C U1=99\nreverse undo U1 bad-uuid A\nq\n"), new PrintWriter(output)).run();
        assertEquals(2, group.snapshot().entries().size());
        assertEquals(130, group.snapshot().balanceOf(U1).grosz());
        assertTrue(output.toString().contains("Błąd danych"));
    }

    @Test
    void equalExpenseSettlementAndHistoryFormCompleteConversation() throws IOException {
        var group = group();
        var output = new StringWriter();
        new ExpenseConsole(group,
                new StringReader("equal U1 100 Obiad U3 U2 U1\nsettle U2 U1 33\nsettle U3 U1 33\nstatus\nhistory\nq\n"),
                new PrintWriter(output)).run();
        assertTrue(output.toString().contains("U1=0.34 PLN"));
        assertTrue(output.toString().contains("Zapisano spłatę"));
        assertTrue(output.toString().contains("U1 Ala: 0.00 PLN"));
        assertTrue(output.toString().contains("Wydatek"));
        assertTrue(output.toString().contains("U2 -> U1"));
        assertEquals(3, group.snapshot().entries().size());
    }

    @Test
    void percentageRemainderAndOverpaymentAreVisible() throws IOException {
        var group = group();
        var output = new StringWriter();
        new ExpenseConsole(group,
                new StringReader("percent U1 100 Obiad U1=3333 U2=3333 U3=3334\nsettle U3 U1 35\nstatus\nq\n"),
                new PrintWriter(output)).run();
        assertTrue(output.toString().contains("U3=0.34 PLN"));
        assertTrue(output.toString().contains("OVERPAYMENT"));
        assertEquals(1, group.snapshot().entries().size());
    }

    @Test
    void malformedCommandsDuplicatesAndUnknownMembersDoNotPublish() throws IOException {
        var group = group();
        var before = group.snapshot();
        var output = new StringWriter();
        new ExpenseConsole(group,
                new StringReader("?\nequal U1\nequal U1 invalid A U1\npercent U1 100 A U1=5000 u1=5000\npercent U1 100 A U1\nequal X 100 A U1\nhelp\nq\n"), new PrintWriter(output)).run();
        assertTrue(output.toString().contains("Błąd danych"));
        assertTrue(output.toString().contains("UNKNOWN_MEMBER"));
        assertSame(before, group.snapshot());
    }

    @ParameterizedTest
    @ValueSource(strings = { "q\n", "" })
    void quitAndEofDoNotUndoRecordedExpense(String ending) throws IOException {
        var group = group();
        new ExpenseConsole(group, new StringReader("equal U1 100 A U2\n" + ending), new PrintWriter(new StringWriter()))
                .run();
        assertEquals(1, group.snapshot().entries().size());
    }

    @Test
    void readFailurePropagatesWithoutChangingJournal() {
        var group = group();
        var before = group.snapshot();
        Reader broken = new Reader() {

            @Override
            public int read(char[] buffer, int offset, int length) throws IOException {
                throw new IOException("Awaria wejścia");
            }

            @Override
            public void close() {
                fail("Adapter nie jest właścicielem strumienia");
            }
        };
        assertThrows(IOException.class, () -> new ExpenseConsole(group, broken, new PrintWriter(new StringWriter()))
                .run());
        assertSame(before, group.snapshot());
    }
}

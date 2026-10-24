package pl.jakubtworek.lld.atm.console;

import pl.jakubtworek.lld.atm.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.atm.Fixtures.*;

/**
 * Test rozmowy sprawdza prezentację i granicę sesji, bez przejmowania System.in/out.
 */
class AtmConsoleTest {

    @Test
    void explicitStaleIdsAndKeyedRetryDoNotAffectCurrentCardOrDuplicateMoney() throws IOException {
        var atm = atm();
        var old = atm.insertCard(C1);
        atm.ejectCard(old);
        var output = new StringWriter();
        run(atm, "insert C2\npin " + old.value() + " 1234\npin 4321\nwithdraw-key one 1000\nwithdraw-key one 1000\n" + "withdraw-key one 2000\nwithdraw-for " + old.value() + " one 1000\neject " + old.value() + "\nbalance\nstatus\nq\n", output);
        var withdrawals = output.toString().lines().filter(line -> line.startsWith("Wypłata ")).toList();
        assertEquals(2, withdrawals.size());
        assertEquals(withdrawals.getFirst(), withdrawals.getLast());
        assertTrue(output.toString().contains("STALE_SESSION"));
        assertTrue(output.toString().contains("IDEMPOTENCY_CONFLICT"));
        assertTrue(output.toString().contains("Saldo: 390.00 PLN"));
        assertEquals(1, atm.snapshot().withdrawals().size());
        assertInstanceOf(Session.Idle.class, atm.snapshot().session());
    }

    @Test
    void adapterTimeoutRejectsRetryAndKeepsCommittedMoney() throws IOException {
        var clock = new MutableClock();
        var atm = new Atm(java.util.List.of(new Account(A1, new Money(100000))), java.util.Map.of(C1, A1), cash(),
                auth(), clock, TTL);
        var output = new StringWriter();
        Reader reader = new Reader() {

            private StringReader part = new StringReader("insert C1\npin 1234\nwithdraw-key one 1000\n");
            private boolean advanced;

            @Override
            public int read(char[] buffer, int offset, int length) throws IOException {
                int result = part.read(buffer, offset, length);
                if (result == -1 && !advanced) {
                    advanced = true;
                    clock.at(NOW.plus(TTL));
                    part = new StringReader("withdraw-key one 1000\nstatus\nq\n");
                    result = part.read(buffer, offset, length);
                }
                return result;
            }

            @Override
            public void close() {
                fail("Adapter nie zamyka wejścia");
            }
        };
        new AtmConsole(atm, reader, new PrintWriter(output)).run();
        assertTrue(output.toString().contains("SESSION_EXPIRED"));
        assertTrue(output.toString().contains("Sesja: IDLE"));
        assertEquals(1, atm.snapshot().withdrawals().size());
        assertEquals(new Money(99000), atm.snapshot().accounts().get(A1).balance());
    }

    @Test
    void cleanupOfOldConversationCannotEjectCardInsertedByAnotherClient() {
        var atm = atm();
        Reader reader = new Reader() {

            private final StringReader commands = new StringReader("insert C1\n");

            @Override
            public int read(char[] buffer, int offset, int length) throws IOException {
                int result = commands.read(buffer, offset, length);
                if (result == -1) {
                    atm.ejectCard(sid(atm));
                    atm.insertCard(C2);
                    throw new IOException("Zmiana klienta przed sprzątaniem starej rozmowy");
                }
                return result;
            }

            @Override
            public void close() {
                fail("Adapter nie zamyka wejścia");
            }
        };
        assertThrows(IOException.class, () -> new AtmConsole(atm, reader, new PrintWriter(new StringWriter())).run());
        assertEquals(C2, assertInstanceOf(Session.CardInserted.class, atm.snapshot().session()).card());
    }

    @Test
    void completeConversationShowsBalanceNotesHistoryAndKeepsWithdrawalAfterEjection() throws IOException {
        var atm = atm();
        var output = new StringWriter();
        run(atm, "insert c1\npin 1234\nbalance\nwithdraw 6000\nhistory\neject\nstatus\nq\n", output);
        assertTrue(output.toString().contains("Saldo: 1000.00 PLN"));
        assertTrue(output.toString().contains("saldo=940.00 PLN"));
        assertTrue(output.toString().contains("banknoty=50 PLN x 1, 10 PLN x 1"));
        assertTrue(output.toString().contains("Potwierdzenie"));
        assertTrue(output.toString().contains("Sesja: IDLE"));
        assertFalse(output.toString().contains("pin 1234"));
        assertEquals(1, atm.snapshot().withdrawals().size());
    }

    @Test
    void thirdRejectedPinEndsSessionAndDoesNotPermitWithdrawal() throws IOException {
        var atm = atm();
        var output = new StringWriter();
        run(atm, "insert C1\npin 0000\npin 0000\npin 0000\nwithdraw 1000\nstatus\nq\n", output);
        assertTrue(output.toString().contains("CARD_EJECTED"));
        assertTrue(output.toString().contains("INVALID_SESSION_STATE"));
        assertTrue(atm.snapshot().withdrawals().isEmpty());
    }

    @Test
    void malformedInputUnknownCardAndReadOnlyCommandsDoNotPublish() throws IOException {
        var atm = atm();
        var before = atm.snapshot();
        var output = new StringWriter();
        run(atm, "?\ninsert\ninsert UNKNOWN\npin abc\nwithdraw text\nbalance\nstatus\nhelp\nq\n", output);
        assertTrue(output.toString().contains("Błąd danych"));
        assertTrue(output.toString().contains("UNKNOWN_CARD"));
        assertSame(before, atm.snapshot());
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "q\n" })
    void eofAndQuitEjectActiveCardWithoutUndoingMoney(String ending) throws IOException {
        var atm = atm();
        run(atm, "insert C1\npin 1234\nwithdraw 1000\n" + ending, new StringWriter());
        assertInstanceOf(Session.Idle.class, atm.snapshot().session());
        assertEquals(new Money(99000), atm.snapshot().accounts().get(A1).balance());
        assertEquals(1, atm.snapshot().withdrawals().size());
    }

    @Test
    void inputFailureEjectsCardAndPreservesAlreadyCommittedMoney() {
        var atm = atm();
        var before = atm.snapshot();
        Reader broken = new Reader() {

            private final StringReader commands = new StringReader("insert C1\npin 1234\nwithdraw 1000\n");

            @Override
            public int read(char[] buffer, int offset, int length) throws IOException {
                int read = commands.read(buffer, offset, length);
                if (read == -1) {
                    throw new IOException("Awaria wejścia");
                }
                return read;
            }

            @Override
            public void close() {
                fail("Adapter nie jest właścicielem strumienia");
            }
        };
        assertThrows(IOException.class, () -> new AtmConsole(atm, broken, new PrintWriter(new StringWriter())).run());
        assertInstanceOf(Session.Idle.class, atm.snapshot().session());
        assertEquals(new Money(99000), atm.snapshot().accounts().get(A1).balance());
        assertEquals(before.cash().total().minus(new Money(1000)), atm.snapshot().cash().total());
        assertEquals(1, atm.snapshot().withdrawals().size());
    }

    private static void run(Atm atm, String commands, StringWriter output) throws IOException {
        new AtmConsole(atm, new StringReader(commands), new PrintWriter(output)).run();
    }
}

package pl.jakubtworek.lld.vendingmachine.console;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.jakubtworek.lld.vendingmachine.Coin;
import pl.jakubtworek.lld.vendingmachine.CoinBundle;
import pl.jakubtworek.lld.vendingmachine.MachinePhase;
import pl.jakubtworek.lld.vendingmachine.Money;
import pl.jakubtworek.lld.vendingmachine.Product;
import pl.jakubtworek.lld.vendingmachine.SlotId;
import pl.jakubtworek.lld.vendingmachine.StockItem;
import pl.jakubtworek.lld.vendingmachine.VendingMachine;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Adapter używa prawdziwego modelu i strumieni w pamięci. Testujemy rozmowę,
 * stan po błędach i politykę zakończenia, bez globalnych System.in/System.out.
 * Nie jest to dowód wydania monety lub produktu przez fizyczny mechanizm.
 */
class VendingConsoleTest {

    private static final SlotId SLOT = new SlotId("A1");

    private static VendingMachine machine() {
        return new VendingMachine(List.of(new StockItem(SLOT, new Product("Woda", new Money(250)), 1)),
                CoinBundle.empty());
    }

    @Test
    void completeJourneyHandlesInsufficientMoneyAndCompletesExactPurchase() throws IOException {
        var machine = machine();
        String output = run(machine, "help\nselect a1\ninsert 200\nbuy\ninsert 50\nbuy\nstatus\nq\n");
        assertTrue(output.contains("[INSUFFICIENT_FUNDS]"));
        assertTrue(output.contains("Wydano: Woda"));
        assertTrue(output.contains("przychód 2.50 PLN"));
        assertEquals(0, machine.snapshot().stock().get(SLOT).quantity());
        assertEquals(MachinePhase.IDLE, machine.snapshot().phase());
    }

    @Test
    void noChangeThenCancelReportsOriginalDenomination() throws IOException {
        var machine = machine();
        String output = run(machine, "select A1\ninsert 500\nbuy\ncancel\nstatus\nq\n");
        assertTrue(output.contains("[NO_EXACT_CHANGE]"));
        assertTrue(output.contains("Zwrot: [PLN5x1] = 5.00 PLN"));
        assertEquals(1, machine.snapshot().stock().get(SLOT).quantity());
        assertEquals(CoinBundle.empty(), machine.snapshot().cash());
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "q\n", " Q \n" })
    void quittingOrEofCancelsExistingPurchaseAndReportsRefund(String ending) throws IOException {
        var machine = machine();
        machine.select(SLOT);
        machine.insert(Coin.PLN5);
        var before = machine.snapshot();
        assertTrue(run(machine, ending).contains("Zwrot przy zakończeniu: [PLN5x1] = 5.00 PLN"));
        assertEquals(MachinePhase.IDLE, machine.snapshot().phase());
        assertEquals(CoinBundle.empty(), machine.snapshot().cash());
        assertEquals(CoinBundle.of(Coin.PLN5), before.escrow());
    }

    @Test
    void malformedInputDoesNotCreatePurchaseAndServiceDoesNotCountAsSale() throws IOException {
        var machine = machine();
        String output = run(machine,
                "\nunknown\nselect\ninsert abc\ninsert 30\nrestock A1 0\n" + "restock A1 1\nload 100\nstatus\nq\n");
        assertTrue(output.contains("Niepoprawna wartość lub polecenie"));
        assertTrue(output.contains("Nieznane polecenie"));
        assertEquals(2, machine.snapshot().stock().get(SLOT).quantity());
        assertEquals(new Money(100), machine.snapshot().cash().total());
        assertEquals(new Money(0), machine.snapshot().revenue());
    }

    @Test
    void readFailurePropagatesWithoutInventingSaleOrAutomaticRefund() {
        var machine = machine();
        machine.select(SLOT);
        machine.insert(Coin.PLN5);
        var before = machine.snapshot();
        Reader broken = new Reader() {

            @Override
            public int read(char[] chars, int offset, int length) throws IOException {
                throw new IOException("Awaria wejścia");
            }

            @Override
            public void close() {
            }
        };
        var adapter = new VendingConsole(machine, broken, new PrintWriter(new StringWriter()));
        assertThrows(IOException.class, adapter::run);
        assertSame(before, machine.snapshot());
        // Awaria I/O nie jest świadomym q/EOF. Właściciel aplikacji otrzymuje
        // wyjątek i nadal może jawnie anulować zachowany model sesji.
        assertEquals(CoinBundle.of(Coin.PLN5), machine.cancel());
    }

    private static String run(VendingMachine machine, String commands) throws IOException {
        var output = new StringWriter();
        new VendingConsole(machine, new StringReader(commands), new PrintWriter(output)).run();
        return output.toString();
    }
}

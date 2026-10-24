package pl.jakubtworek.lld.promotion.console;

import org.junit.jupiter.api.Test;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Testy tekstowego adaptera przechodzą przez realne komendy, nie tylko klasy
 * domenowe. Strumienie są wstrzykiwane, więc test nie zmienia globalnego System.in.
 */
class ConsoleMainTest {

    @Test
    void demoShowsPartialCapExclusionAndExclusiveCoupon() {
        String output = run("add BOOK books 100.00 1\ntier vip\nquote\ncoupon welcome\nquote\n" + "uncoupon welcome\ncoupon FLASH50\nquote\nq\n");
        assertFalse(output.contains("Odmowa:"));
        assertTrue(output.contains("Przed: 100.00 PLN; rabat: 25.00 PLN; do zapłaty: 75.00 PLN"));
        assertTrue(output.contains("BOOKS20: APPLIED; propozycja: 20.00 PLN; zastosowano: 15.00 PLN"));
        assertTrue(output.contains("BOOKS20: EXCLUDED"));
        assertTrue(output.contains("blokuje: WELCOME15"));
        assertTrue(output.contains("VIP10: BLOCKED_EXCLUSIVE"));
        assertTrue(output.contains("FLASH50: APPLIED; propozycja: 50.00 PLN; zastosowano: 25.00 PLN"));
    }

    @Test
    void rejectedInputsLeavePreviousCartAndTierAvailable() {
        String output = run("add A books 10.00 2\nadd A books 1.00 1\nadd B tech 1.001 1\n" + "add B tech 1.00 0\ntier vip\ntier wrong\nremove missing\nbasket\nquote\n");
        assertTrue(output.contains("Odmowa:"));
        assertTrue(output.contains("Suma koszyka: 20.00 PLN"));
        assertTrue(output.contains("Przed: 20.00 PLN; rabat: 2.00 PLN; do zapłaty: 18.00 PLN"));
    }

    @Test
    void overflowingCartAdditionDoesNotPublishItsCandidate() {
        String output = run("add A tech 92233720368547758.07 1\nadd B tech 0.01 1\nbasket\nremove A\nquote\nq\n");
        assertTrue(output.contains("Odmowa:"));
        assertTrue(output.contains("Suma koszyka: 92233720368547758.07 PLN"));
        assertTrue(output.contains("Przed: 0.00 PLN; rabat: 0.00 PLN; do zapłaty: 0.00 PLN"));
        assertFalse(output.contains("B tech"));
    }

    @Test
    void resetAndEofAreSupportedAndHelpDescribesCommands() {
        String output = run("help\npromotions\nadd A books 100 1\ncoupon WELCOME\ntier vip\nnew\nquote\n");
        assertTrue(output.contains("add SKU category price quantity"));
        assertTrue(output.contains("FLASH50: priorytet 200"));
        assertTrue(output.contains("Nowy koszyk"));
        assertTrue(output.contains("Przed: 0.00 PLN; rabat: 0.00 PLN; do zapłaty: 0.00 PLN"));
        assertTrue(output.contains("VIP10: CONDITION_NOT_MET"));
    }

    @Test
    void policyScopeAndCampaignCanBeExploredFromConsole() {
        String output = run("add A books 1.00 1\nadd B tech 99.00 1\ncoupon BOOKS10\npolicy best-single\nquote\n" + "uncoupon BOOKS10\ncoupon VALID5\nat 2026-10-22T00:00:00Z\nquote\n" + "at 2026-10-21T00:00:00Z\nquote\nat invalid\npolicy wrong\nquote\nq\n");
        assertTrue(output.contains("Polityka: best-single"));
        // BOOKS20 daje więcej niż 10% od książki za 1 PLN.
        assertTrue(output.contains("BOOKS10: NOT_SELECTED"));
        assertTrue(output.contains("VALID5: NOT_ACTIVE"));
        assertTrue(output.contains("actual=2026-10-21T00:00:00Z"));
        assertTrue(output.contains("przycięcia:"));
        assertTrue(output.contains("Linia B:"));
        assertTrue(output.contains("Odmowa:"));
    }

    private static String run(String commands) {
        var output = new StringWriter();
        ConsoleMain.run(new StringReader(commands), new PrintWriter(output));
        return output.toString();
    }
}

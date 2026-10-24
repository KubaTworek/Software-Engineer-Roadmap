package pl.jakubtworek.lld.chess.console;

import org.junit.jupiter.api.Test;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test adaptera bada realny tekst i zachowanie po odmowie, bez zamiany System.in/out.
 */
class ConsoleMainTest {

    @Test
    void invalidLoadLeavesPreviousGameAndUndoRestoresOriginalFen() {
        String output = run("e2 e4\nload bad fen\nfen\nundo\nfen\nq\n");
        assertTrue(output.contains("Odmowa:"));
        assertTrue(output.contains("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"));
        assertTrue(output.contains("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"));
    }

    @Test
    void fenClaimResignationUndoAndDivideAreAccessible() {
        String output = run("help\nload 7k/8/8/8/8/8/8/KR6 w - - 99 80\n" + "claim white fifty b1 b2\nundo\nresign black\nundo\nclaims\nperft 1\ndivide 1\nq\n");
        assertFalse(output.contains("Odmowa:"));
        assertTrue(output.contains("Wynik: FIFTY_MOVE_RULE"));
        assertTrue(output.contains("Wynik: RESIGNATION"));
        assertTrue(output.contains("Perft:"));
        assertTrue(output.contains("Suma:"));
    }

    @Test
    void promotionAndInputRefusalsDoNotBreakSession() {
        String output = run("undo\nperft 5\ndivide 0\nresign green\n" + "load 7k/4P3/8/8/8/8/8/K7 w - - 0 1\ne7 e8 X\ne7 e8 N\nfen\n");
        assertTrue(output.contains("NO_HISTORY"));
        assertTrue(output.contains("Głębokość konsoli"));
        assertTrue(output.contains("4N2k/8/8/8/8/8/8/K7 b - - 0 1"));
    }

    private static String run(String commands) {
        var output = new StringWriter();
        ConsoleMain.run(new StringReader(commands), new PrintWriter(output));
        return output.toString();
    }
}

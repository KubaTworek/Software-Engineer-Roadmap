package pl.jakubtworek.lld.tictactoe.console;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.jakubtworek.lld.tictactoe.GameRules;
import pl.jakubtworek.lld.tictactoe.GameStatus;
import pl.jakubtworek.lld.tictactoe.TicTacToeGame;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Cel edukacyjny: przetestować adapter razem z prawdziwą domeną, ale zastąpić
 * urządzenie wejścia/wyjścia strumieniami w pamięci. To nie mock reguł gry:
 * nadal działa TicTacToeGame. Asercje sprawdzają wynik i istotne komunikaty,
 * bez przywiązywania wszystkich testów do każdego odstępu w całej rozmowie.
 * Testy nie dowodzą poprawnego kodowania dowolnego terminala ani obsługi awarii
 * fizycznego urządzenia. Start gotowego JAR-a sprawdzamy osobno.
 */
class ConsoleGameTest {

    @Test
    void playsFullGameAndPrintsFinalBoardAndWinner() throws IOException {
        var game = new TicTacToeGame();
        String output = run(game, "0 0\n1 0\n0 1\n1 1\n0 2\n");
        assertEquals(GameStatus.X_WON, game.snapshot().status());
        assertTrue(output.contains("X X X"));
        assertTrue(output.endsWith("Wygrywa X!" + System.lineSeparator()));
    }

    @Test
    void printsDrawForFullBoardWithoutWinner() throws IOException {
        var game = new TicTacToeGame();
        String output = run(game, "0 0\n0 1\n0 2\n1 1\n1 0\n1 2\n2 1\n2 0\n2 2\n");
        assertEquals(GameStatus.DRAW, game.snapshot().status());
        assertTrue(output.endsWith("Remis." + System.lineSeparator()));
    }

    @Test
    void malformedAndIllegalInputDoesNotConsumeTurnAndGameCanContinue() throws IOException {
        // „tekst” i przepełniona liczba naruszają format, natomiast -1 0 i 3 0
        // poprawnie opisują pozycję, lecz naruszają granice planszy. Na końcu
        // wykonujemy wygrywającą sekwencję: odmowy muszą pozwolić kontynuować grę.
        var game = new TicTacToeGame();
        String output = run(game, "tekst\n\n0\n0 0 1\n999999999999 0\n-1 0\n3 0\n" + "0 0\n0 0\n1 0\n0 1\n1 1\n0 2\n");
        assertTrue(output.contains("Błędny format"));
        assertTrue(output.contains("[OUT_OF_BOUNDS]"));
        assertTrue(output.contains("[OCCUPIED]"));
        assertEquals(GameStatus.X_WON, game.snapshot().status());
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "q\n", " Q \n" })
    void eofAndQuitLeaveUnfinishedGameUnchanged(String input) throws IOException {
        var game = new TicTacToeGame();
        var before = game.snapshot();
        assertTrue(run(game, input).contains("Zakończono demo"));
        assertSame(before, game.snapshot());
    }

    @Test
    void consoleUsesConfiguredBoardAndWinningLength() throws IOException {
        var game = new TicTacToeGame(new GameRules(5, 4));
        String output = run(game, "2 0\n0 0\n2 1\n0 1\n2 2\n0 2\n2 3\n");
        assertEquals(GameStatus.X_WON, game.snapshot().status());
        assertTrue(output.contains("X X X X ."));
    }

    @Test
    void rendererAlignsTwoDigitCoordinatesWithoutChangingSnapshot() {
        var game = new TicTacToeGame(new GameRules(11, 11));
        var before = game.snapshot();
        String board = new BoardRenderer().render(before.board());
        String[] lines = board.split("\n");
        assertTrue(lines[0].contains(" 9 10"));
        assertTrue(lines[11].startsWith("10 "));
        assertSame(before, game.snapshot());
    }

    private static String run(TicTacToeGame game, String input) throws IOException {
        // Prawdziwy adapter czyta scenariusz jak dane od użytkownika, ale test
        // nie blokuje na klawiaturze i nie zmienia globalnych strumieni procesu.
        var output = new StringWriter();
        new ConsoleGame(game, new StringReader(input), new PrintWriter(output)).run();
        return output.toString();
    }
}

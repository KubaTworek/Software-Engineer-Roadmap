package pl.jakubtworek.lld.tictactoe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.tictactoe.Mark.*;

/**
 * Scenariusze zmiany wymagań: zwycięska linia może być krótsza niż bok planszy
 * i może znajdować się na przekątnej nieprzechodzącej przez narożniki planszy.
 * Takie przypadki obnażają ograniczenia algorytmu „sprawdź osiem linii 3 x 3”.
 * Cel edukacyjny: sprawdzić rozszerzenie modelu przez nowe zachowanie, nie
 * przez asercję, że do konstruktora dodano parametr. Granice K=1, K<N i N=100
 * mają odmienne pułapki. Ta klasa wybiera reprezentatywne przypadki, a nie
 * wszystkie możliwe przebiegi; szerszą próbkę zapewnia GeneratedGameTest.
 */
class ConfigurableGameTest {

    @ParameterizedTest
    @CsvSource({ "1,0,0", "0,1,0", "1,1,0", "1,-1,3" })
    void detectsLengthFourOnFiveByFiveBoard(int dr, int dc, int startColumn) {
        var game = new TicTacToeGame(new GameRules(5, 4));
        // Zaczynamy w wierszu 1; pierwsza przekątna (1,0)->(4,3) jest poboczna,
        // a druga (1,3)->(4,0) leży na głównej antyprzekątnej. Wraz z pionem
        // i poziomem sprawdzają cztery osie. O ma tylko trzy znaki w wierszu 0,
        // więc nie może wcześniej zakończyć partii przy K=4.
        for (int i = 0; i < 4; i++) {
            var result = game.play(X, new Position(1 + i * dr, startColumn + i * dc));
            if (i < 3) {
                assertEquals(GameStatus.IN_PROGRESS, result.status());
                game.play(O, new Position(0, i));
            }
        }
        assertEquals(GameStatus.X_WON, game.snapshot().status());
    }

    @Test
    void fillingGapCanCreateLineLongerThanRequired() {
        // Cztery X są rozdzielone luką, więc sama ich liczba nie daje wygranej.
        // Wstawienie środka tworzy pięć kolejnych X. Reguła >=K zaakceptuje
        // wynik, a błędne ==K albo liczenie jednej strony osi go przeoczy.
        var game = new TicTacToeGame(new GameRules(5, 4));
        int[] columns = { 0, 1, 3, 4 };
        for (int column : columns) {
            game.play(X, new Position(2, column));
            game.play(O, new Position(0, column));
        }
        assertEquals(GameStatus.IN_PROGRESS, game.snapshot().status());
        assertEquals(GameStatus.X_WON, game.play(X, new Position(2, 2)).status());
    }

    @Test
    void opponentsMarkInterruptsLineEvenIfTotalNumberOfXsIsEnough() {
        var game = new TicTacToeGame(new GameRules(5, 4));
        game.play(X, new Position(2, 0));
        game.play(O, new Position(2, 2));
        game.play(X, new Position(2, 1));
        game.play(O, new Position(0, 0));
        game.play(X, new Position(2, 3));
        game.play(O, new Position(0, 1));
        assertEquals(GameStatus.IN_PROGRESS, game.play(X, new Position(2, 4)).status());
    }

    @Test
    void oneCellBoardEndsWithWinRatherThanDraw() {
        // Najmniejszy przykład odróżniający kolejność win/full. Jest łatwiejszy
        // do ręcznego prześledzenia niż dziewięć ruchów na planszy klasycznej.
        var game = new TicTacToeGame(new GameRules(1, 1));
        var result = game.play(X, new Position(0, 0));
        assertEquals(GameStatus.X_WON, result.status());
        assertTrue(result.board().isFull());
        assertTrue(result.nextPlayer().isEmpty());
    }

    @Test
    void lengthOneWinsOnFirstMoveEvenOnLargerBoard() {
        var game = new TicTacToeGame(new GameRules(5, 1));
        assertEquals(GameStatus.X_WON, game.play(X, new Position(4, 4)).status());
    }

    @Test
    void supportsUpperConfiguredBoardBoundary() {
        var game = new TicTacToeGame(new GameRules(100, 100));
        assertEquals(GameStatus.IN_PROGRESS, game.play(X, new Position(99, 99)).status());
        var before = game.snapshot();
        assertThrows(IllegalMoveException.class, () -> game.play(O, new Position(100, 99)));
        assertSame(before, game.snapshot());
    }
}

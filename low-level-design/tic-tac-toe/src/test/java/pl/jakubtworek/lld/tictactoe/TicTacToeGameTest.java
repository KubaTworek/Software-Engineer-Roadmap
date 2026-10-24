package pl.jakubtworek.lld.tictactoe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.Optional;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.tictactoe.IllegalMoveException.Reason.*;
import static pl.jakubtworek.lld.tictactoe.Mark.*;

/**
 * Edukacyjna specyfikacja zachowania: start, tury, odmowy i stany terminalne.
 * Czytaj test jako „dany stan -> polecenie -> obserwowalny skutek”. Nie
 * wywołujemy prywatnego algorytmu, więc inna poprawna implementacja może
 * spełnić tę samą specyfikację. Testy parametrów i minimalnych przykładów
 * ułatwiają zlokalizowanie błędu przed analizą pełnego drzewa historii.
 */
class TicTacToeGameTest {

    @Test
    void startsWithEmptyBoardAndXThenAlternatesTurns() {
        var game = new TicTacToeGame();
        assertEquals(GameStatus.IN_PROGRESS, game.snapshot().status());
        assertEquals(Optional.of(X), game.snapshot().nextPlayer());
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                assertTrue(game.snapshot().board().markAt(p(row, column)).isEmpty());
            }
        }
        var afterX = game.play(X, p(0, 0));
        assertEquals(Optional.of(X), afterX.board().markAt(p(0, 0)));
        assertEquals(Optional.of(O), afterX.nextPlayer());
        assertSame(afterX, game.snapshot());
        assertEquals(Optional.of(X), game.play(O, p(1, 1)).nextPlayer());
    }

    @Test
    void rejectedMovesPreserveEntireStateAndDoNotConsumeTurn() {
        var game = new TicTacToeGame();
        // Odmowa na pustej planszy, po ruchu i po zajęciu pola sprawdza różne
        // warstwy kontraktu. Dopiero kolejny poprawny ruch potwierdza, że
        // odrzucone polecenie nie zużyło tury ani nie uszkodziło partii.
        assertRejected(game, O, p(0, 0), WRONG_TURN);
        assertRejected(game, X, p(-1, 0), OUT_OF_BOUNDS);
        assertRejected(game, X, p(3, 0), OUT_OF_BOUNDS);
        assertRejected(game, X, p(0, -1), OUT_OF_BOUNDS);
        assertRejected(game, X, p(0, 3), OUT_OF_BOUNDS);
        assertRejected(game, X, p(Integer.MAX_VALUE, Integer.MIN_VALUE), OUT_OF_BOUNDS);
        game.play(X, p(0, 0));
        assertRejected(game, X, p(0, 1), WRONG_TURN);
        assertRejected(game, O, p(0, 0), OCCUPIED);
        assertEquals(Optional.of(X), game.play(O, p(1, 1)).nextPlayer());
    }

    @ParameterizedTest
    @MethodSource("winningLines")
    void detectsEveryClassicWinningAxisIncludingMoveInMiddle(int[][] line) {
        var game = new TicTacToeGame();
        // O zajmuje dwa pola spoza badanej linii. Nie może wygrać po dwóch
        // ruchach; nie maskuje więc sprawdzanej reguły zwycięstwa X.
        var free = Stream.of(p(0, 0), p(0, 1), p(0, 2), p(1, 0), p(1, 1), p(1, 2), p(2, 0), p(2, 1), p(2, 2))
                .filter(position -> Stream.of(line).noneMatch(cell -> position.equals(p(cell[0], cell[1])))).toList();
        game.play(X, p(line[0][0], line[0][1]));
        game.play(O, free.get(0));
        game.play(X, p(line[2][0], line[2][1]));
        game.play(O, free.get(1));
        var won = game.play(X, p(line[1][0], line[1][1]));
        assertEquals(GameStatus.X_WON, won.status());
        assertEquals(Optional.of(X), won.status().winner());
        assertTrue(won.nextPlayer().isEmpty());
        assertRejected(game, O, free.get(2), GAME_FINISHED);
    }

    static Stream<int[][]> winningLines() {
        return Stream.of(new int[][] { { 0, 0 }, { 0, 1 }, { 0, 2 } }, new int[][] { { 1, 0 }, { 1, 1 }, { 1, 2 } },
                new int[][] { { 2, 0 }, { 2, 1 }, { 2, 2 } }, new int[][] { { 0, 0 }, { 1, 0 }, { 2, 0 } },
                new int[][] { { 0, 1 }, { 1, 1 }, { 2, 1 } }, new int[][] { { 0, 2 }, { 1, 2 }, { 2, 2 } },
                new int[][] { { 0, 0 }, { 1, 1 }, { 2, 2 } }, new int[][] { { 0, 2 }, { 1, 1 }, { 2, 0 } });
    }

    @Test
    void oCanWinAndNoMoveIsAcceptedAfterwards() {
        var game = playClassic(new int[][] { { 0, 0 }, { 1, 0 }, { 0, 1 }, { 1, 1 }, { 2, 2 }, { 1, 2 } });
        assertEquals(GameStatus.O_WON, game.snapshot().status());
        assertEquals(Optional.of(O), game.snapshot().status().winner());
        assertTrue(game.snapshot().nextPlayer().isEmpty());
        assertRejected(game, X, p(2, 0), GAME_FINISHED);
    }

    @Test
    void fullBoardWithoutWinnerIsDrawAndRejectsFurtherMoves() {
        var game = playClassic(new int[][] { { 0, 0 }, { 0, 1 }, { 0, 2 }, { 1, 1 }, { 1, 0 }, { 1, 2 }, { 2, 1 }, { 2,
                0 }, { 2, 2 } });
        assertEquals(GameStatus.DRAW, game.snapshot().status());
        assertTrue(game.snapshot().board().isFull());
        assertTrue(game.snapshot().status().winner().isEmpty());
        assertTrue(game.snapshot().nextPlayer().isEmpty());
        // Nawet dla zajętego pola najpierw obowiązuje zakaz gry po końcu partii.
        assertRejected(game, X, p(0, 0), GAME_FINISHED);
    }

    @Test
    void winningOnLastFreeCellTakesPrecedenceOverDraw() {
        var game = playClassic(new int[][] { { 0, 0 }, { 0, 1 }, { 1, 1 }, { 0, 2 }, { 1, 2 }, { 1, 0 }, { 2, 0 }, { 2,
                1 }, { 2, 2 } });
        assertTrue(game.snapshot().board().isFull());
        assertEquals(GameStatus.X_WON, game.snapshot().status());
    }

    @Test
    void archivedSnapshotRemainsUnchangedAfterLaterMoves() {
        // Zachowujemy widoki przed zmianami. Samo sprawdzenie aktualnej
        // planszy nie ujawniłoby przypadkowej modyfikacji historycznych danych.
        var game = new TicTacToeGame();
        var empty = game.snapshot();
        var afterX = game.play(X, p(0, 0));
        game.play(O, p(2, 2));
        assertTrue(empty.board().markAt(p(0, 0)).isEmpty());
        assertEquals(Optional.of(X), empty.nextPlayer());
        assertTrue(afterX.board().markAt(p(2, 2)).isEmpty());
        assertEquals(Optional.of(O), afterX.nextPlayer());
        assertEquals(Optional.of(O), game.snapshot().board().markAt(p(2, 2)));
    }

    @Test
    void separateGamesDoNotShareState() {
        var first = new TicTacToeGame();
        var second = new TicTacToeGame();
        first.play(X, p(0, 0));
        assertTrue(second.snapshot().board().markAt(p(0, 0)).isEmpty());
        assertEquals(Optional.of(X), second.snapshot().nextPlayer());
    }

    @Test
    void nullCommandsAreProgrammingErrorsAndPreserveState() {
        var game = new TicTacToeGame();
        var before = game.snapshot();
        assertThrows(NullPointerException.class, () -> game.play(null, p(0, 0)));
        assertThrows(NullPointerException.class, () -> game.play(X, null));
        assertSame(before, game.snapshot());
        assertThrows(NullPointerException.class, () -> new TicTacToeGame(null));
    }

    @ParameterizedTest
    @CsvSource({ "0,1", "-1,1", "101,3", "3,0", "3,-1", "3,4" })
    void rejectsImpossibleConfiguration(int size, int length) {
        assertThrows(IllegalArgumentException.class, () -> new GameRules(size, length));
    }

    @Test
    void snapshotDoesNotAllowNextPlayerForFinishedGameOrMissingPlayerForActiveGame() {
        // Test znajduje się w pakiecie domeny, więc może celowo naruszyć
        // wewnętrzny kontrakt konstruktora. Klient z pakietu console tego
        // konstruktora nie widzi. Sprawdzamy tylko lokalną zależność statusu
        // i tury, nie dowód legalności dowolnej planszy przekazanej ręcznie.
        var board = new TicTacToeGame().snapshot().board();
        assertThrows(IllegalArgumentException.class, () -> new GameSnapshot(board, GameStatus.DRAW, Optional.of(X)));
        assertThrows(IllegalArgumentException.class, () -> new GameSnapshot(board, GameStatus.IN_PROGRESS,
                Optional.empty()));
    }

    private static TicTacToeGame playClassic(int[][] moves) {
        var game = new TicTacToeGame();
        Mark next = X;
        for (int[] move : moves) {
            game.play(next, p(move[0], move[1]));
            next = next.opponent();
        }
        return game;
    }

    private static void assertRejected(TicTacToeGame game, Mark mark, Position position,
            IllegalMoveException.Reason reason) {
        var before = game.snapshot();
        var error = assertThrows(IllegalMoveException.class, () -> game.play(mark, position));
        assertEquals(reason, error.reason());
        // Porównanie tożsamości jest tu celowe: odmowa ma zachować dokładnie
        // ten sam opublikowany widok. Nie sprawdzamy prywatnych tablic ani
        // liczby wywołań pomocników. Brak zmiany stanu jest kontraktem play.
        assertSame(before, game.snapshot(), "Odmowa nie może opublikować nowego stanu");
    }

    private static Position p(int row, int column) {
        return new Position(row, column);
    }
}

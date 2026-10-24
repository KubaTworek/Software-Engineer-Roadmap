package pl.jakubtworek.lld.tictactoe;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.tictactoe.Mark.*;

/**
 * Test modelowy przechodzi wszystkie legalne historie klasycznej gry.
 * Wyrocznia celowo różni się od implementacji: skanuje osiem stałych trójek,
 * zamiast liczyć osie przechodzące przez ostatni ruch. Błąd badanego
 * algorytmu ma więc szansę zostać wykryty, a nie powielony w oczekiwaniu testu.
 * Cel edukacyjny: zobaczyć różnicę pomiędzy pojedynczym przykładem a pełnym
 * przeglądem skończonej przestrzeni historii. Wynik dotyczy wyłącznie 3x3/K=3
 * i legalnych ruchów; nie dowodzi działania dla każdego N ani odmów przy null.
 */
class ClassicGameTreeTest {

    private static final int[][] LINES = { { 0, 1, 2 }, { 3, 4, 5 }, { 6, 7, 8 }, { 0, 3, 6 }, { 1, 4, 7 }, { 2, 5, 8 },
            { 0, 4, 8 }, { 2, 4, 6 } };

    @Test
    void everyLegalClassicHistoryAgreesWithIndependentFullBoardOracle() {
        // Liczymy historie, a nie unikalne pozycje: różne kolejności ruchów
        // prowadzące do tej samej planszy są osobnymi ścieżkami drzewa.
        int[] outcomes = new int[3];
        visit(new int[9], new int[9], 0, outcomes);
        assertArrayEquals(new int[] { 131_184, 77_904, 46_080 }, outcomes);
    }

    private static void visit(int[] board, int[] history, int depth, int[] outcomes) {
        // Odtworzenie przez PUBLICZNE API chroni przed użyciem testowego
        // konstruktora, który mógłby ominąć prawdziwą walidację domeny.
        var game = new TicTacToeGame();
        for (int i = 0; i < depth; i++) {
            game.play(i % 2 == 0 ? X : O, new Position(history[i] / 3, history[i] % 3));
        }
        var expected = outcome(board, depth);
        assertEquals(expected, game.snapshot().status());
        if (expected != GameStatus.IN_PROGRESS) {
            assertTrue(game.snapshot().nextPlayer().isEmpty());
            outcomes[switch(expected) {
                case X_WON ->
                    0;
                case O_WON ->
                    1;
                case DRAW ->
                    2;
                default ->
                    throw new AssertionError("Stan nieterminalny");
            }]++;
            return;
        }
        assertEquals(depth % 2 == 0 ? X : O, game.snapshot().nextPlayer().orElseThrow());
        for (int cell = 0; cell < 9; cell++) {
            if (board[cell] == 0) {
                // Backtracking: wybieramy wolne pole, odwiedzamy całą gałąź,
                // a potem usuwamy znak z wyroczni przed badaniem rodzeństwa.
                // Bez cofnięcia board[cell] test pominąłby część historii.
                // Używamy mutowalnego bufora wyłącznie jako narzędzia testu;
                // partia jest odtwarzana osobno przez swoje publiczne API.
                board[cell] = depth % 2 == 0 ? 1 : 2;
                history[depth] = cell;
                visit(board, history, depth + 1, outcomes);
                board[cell] = 0;
            }
        }
    }

    private static GameStatus outcome(int[] board, int depth) {
        // Indeksy 0..8 traktujemy jako planszę wierszami (r=cell/3, c=cell%3).
        // Stałe trójki są prostą, niezależną wyrocznią dla tego jednego wariantu.
        for (int[] line : LINES) {
            int mark = board[line[0]];
            if (mark != 0 && mark == board[line[1]] && mark == board[line[2]]) {
                return mark == 1 ? GameStatus.X_WON : GameStatus.O_WON;
            }
        }
        return depth == 9 ? GameStatus.DRAW : GameStatus.IN_PROGRESS;
    }
}

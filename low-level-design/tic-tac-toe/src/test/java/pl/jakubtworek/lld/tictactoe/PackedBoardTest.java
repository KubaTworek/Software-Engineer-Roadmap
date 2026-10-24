package pl.jakubtworek.lld.tictactoe;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Dowód granicy liczby kopiowanych węzłów oraz bitów na granicach słów.
 */
class PackedBoardTest {

    @Test
    void updatesCopyLogarithmicPathAndPreserveOldBitplanes() {
        Board empty = Board.empty(100), board = empty;
        assertEquals(9, board.copiedNodesPerMove());
        for (int index : new int[] { 0, 63, 64, 127, 128, 9999 }) {
            var position = new Position(index / 100, index % 100);
            var old = board;
            board = board.afterMove(Mark.X, position);
            assertTrue(old.markAt(position).isEmpty());
            assertEquals(Mark.X, board.markAt(position).orElseThrow());
        }
        assertTrue(empty.markAt(new Position(0, 0)).isEmpty());
        assertEquals(1, Board.empty(3).copiedNodesPerMove());
    }
}

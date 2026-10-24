package pl.jakubtworek.lld.chess.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Wyrocznia wychodzi od figur i sprawdza drogę; badany kod od celu po promieniach.
 */
class AttackIndexTest {

    @Test
    void reverseRaysAgreeWithIndependentPieceScanOnGeneratedBoards() {
        var random = new Random(100019);
        var rules = new ChessRules();
        for (int sample = 0; sample < 100; sample++) {
            var pieces = new HashMap<Square, Piece>();
            for (int i = 0; i < 40; i++) {
                pieces.put(new Square(random.nextInt(8), random.nextInt(8)),
                        new Piece(Color.values()[random.nextInt(2)], PieceType.values()[random.nextInt(6)]));
            }
            var board = new Board(pieces);
            for (int target = 0; target < 64; target++) {
                var square = new Square(target & 7, target >>> 3);
                for (Color color : Color.values()) {
                    assertEquals(scan(pieces, square, color), rules.attacked(board, square, color));
                }
            }
        }
    }

    private boolean scan(Map<Square, Piece> pieces, Square target, Color color) {
        for (var entry : pieces.entrySet()) {
            if (entry.getValue().color() != color) {
                continue;
            }
            Square source = entry.getKey();
            int dx = target.file() - source.file(), dy = target.rank() - source.rank();
            int x = Math.abs(dx), y = Math.abs(dy);
            if (x == 0 && y == 0) {
                continue;
            }
            boolean geometry = switch(entry.getValue().type()) {
                case PAWN ->
                    x == 1 && dy == color.forward();
                case KNIGHT ->
                    x * y == 2;
                case KING ->
                    Math.max(x, y) == 1;
                case ROOK ->
                    x == 0 || y == 0;
                case BISHOP ->
                    x == y;
                case QUEEN ->
                    x == 0 || y == 0 || x == y;
            };
            if (!geometry) {
                continue;
            }
            if (entry.getValue().type() == PieceType.KNIGHT) {
                return true;
            }
            boolean clear = true;
            for (int f = source.file() + Integer.signum(dx), r = source.rank() + Integer.signum(dy); f != target.file()
                    || r != target.rank(); f += Integer.signum(dx), r += Integer.signum(dy)) {
                if (pieces.containsKey(new Square(f, r))) {
                    clear = false;
                    break;
                }
            }
            if (clear) {
                return true;
            }
        }
        return false;
    }
}

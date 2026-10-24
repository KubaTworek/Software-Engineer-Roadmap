package pl.jakubtworek.lld.chess.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Tożsamość na potrzeby powtórzeń: figury, tura, trwałe prawa i LEGALNE en passant.
 * Pomijamy liczniki. Surowy cel FEN nie różnicuje pozycji, jeśli nie da się bić
 * (np. pion jest związany). Rekord daje porównanie treści mapy, nie jej kolejności.
 */
public record PositionKey(Map<Square, Piece> pieces, Color turn, CastlingRights rights, Square legalEnPassant) {

    public PositionKey {
        pieces = Map.copyOf(pieces);
        Objects.requireNonNull(turn);
        Objects.requireNonNull(rights);
    }

    public static PositionKey of(Position position) {
        Square legalEp = null;
        Square target = position.enPassantTarget().orElse(null);
        if (target != null) {
            var rules = new ChessRules();
            for (int dx : new int[] { -1, 1 }) {
                int file = target.file() + dx, rank = target.rank() - position.turn().forward();
                if (file < 0 || file > 7 || rank < 0 || rank > 7)
                    continue;
                Square from = new Square(file, rank);
                if (position.board().at(from).filter(new Piece(position.turn(), PieceType.PAWN)::equals).isPresent()
                        && rules.evaluate(position, new Move(from, target)) instanceof MoveEvaluation.Accepted) {
                    legalEp = target;
                    break;
                }
            }
        }
        return new PositionKey(position.board().pieces(), position.turn(), position.rights(), legalEp);
    }
}

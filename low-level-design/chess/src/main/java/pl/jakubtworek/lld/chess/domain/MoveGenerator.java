package pl.jakubtworek.lld.chess.domain;

import java.util.ArrayList;
import java.util.List;
import static pl.jakubtworek.lld.chess.domain.PieceType.*;

/**
 * Generuje ruchy pseudolegalne: pasujące do geometrii i zajętości pól, ale
 * potencjalnie odsłaniające króla. Roszadę proponuje nawet bez historycznego
 * prawa; ostatecznym autorytetem pozostaje ChessRules.evaluate. Rozdzielenie
 * zmniejsza liczbę kandydatów bez tworzenia drugiej implementacji szacha.
 */
final class MoveGenerator {

    private static final int[][] ORTHOGONAL = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
    private static final int[][] DIAGONAL = { { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } };
    private static final int[][] KNIGHT_STEPS = { { 1, 2 }, { 2, 1 }, { -1, 2 }, { -2, 1 }, { 1, -2 }, { 2, -1 }, { -1,
            -2 }, { -2, -1 } };

    List<Move> candidates(Position position) {
        var moves = new ArrayList<Move>();
        for (var entry : position.board().pieces().entrySet()) {
            Piece piece = entry.getValue();
            if (piece.color() != position.turn())
                continue;
            Square from = entry.getKey();
            switch(piece.type()) {
                case PAWN ->
                    pawn(position, from, moves);
                case KNIGHT ->
                    rays(position, from, KNIGHT_STEPS, false, moves);
                case KING ->
                    {
                        rays(position, from, ORTHOGONAL, false, moves);
                        rays(position, from, DIAGONAL, false, moves);
                        if (from.equals(new Square(4, piece.color().homeRank()))) {
                            add(from, new Square(6, from.rank()), piece, moves);
                            add(from, new Square(2, from.rank()), piece, moves);
                        }
                    }
                case ROOK ->
                    rays(position, from, ORTHOGONAL, true, moves);
                case BISHOP ->
                    rays(position, from, DIAGONAL, true, moves);
                case QUEEN ->
                    {
                        rays(position, from, ORTHOGONAL, true, moves);
                        rays(position, from, DIAGONAL, true, moves);
                    }
            }
        }
        return List.copyOf(moves);
    }

    private void rays(Position position, Square from, int[][] directions, boolean sliding, List<Move> moves) {
        Piece piece = position.board().at(from).orElseThrow();
        for (int[] direction : directions) {
            int file = from.file() + direction[0], rank = from.rank() + direction[1];
            while (inside(file, rank)) {
                Square to = new Square(file, rank);
                Piece target = position.board().at(to).orElse(null);
                if (target == null || target.color() != piece.color())
                    add(from, to, piece, moves);
                // Pierwsza figura zamyka promień, także przeciwnik, którego można bić.
                if (!sliding || target != null)
                    break;
                file += direction[0];
                rank += direction[1];
            }
        }
    }

    private void pawn(Position position, Square from, List<Move> moves) {
        Piece pawn = position.board().at(from).orElseThrow();
        int rank = from.rank() + pawn.color().forward();
        if (!inside(from.file(), rank))
            return;
        Square ahead = new Square(from.file(), rank);
        if (position.board().at(ahead).isEmpty()) {
            add(from, ahead, pawn, moves);
            if (from.rank() == pawn.color().pawnRank()) {
                Square twice = new Square(from.file(), rank + pawn.color().forward());
                if (position.board().at(twice).isEmpty())
                    add(from, twice, pawn, moves);
            }
        }
        for (int dx : new int[] { -1, 1 }) {
            if (!inside(from.file() + dx, rank))
                continue;
            Square to = new Square(from.file() + dx, rank);
            if (position.board().at(to).filter(p -> p.color() != pawn.color()).isPresent() || position.enPassantTarget()
                    .filter(to::equals).isPresent())
                add(from, to, pawn, moves);
        }
    }

    private void add(Square from, Square to, Piece piece, List<Move> moves) {
        // Niedopromocje są pełnoprawnymi alternatywami; domyślny hetman gubiłby ruchy.
        if (piece.type() == PAWN && to.rank() == piece.color().promotionRank()) {
            for (PieceType choice : List.of(QUEEN, ROOK, BISHOP, KNIGHT)) moves.add(new Move(from, to, choice));
        } else
            moves.add(new Move(from, to));
    }

    private boolean inside(int file, int rank) {
        return file >= 0 && file < 8 && rank >= 0 && rank < 8;
    }
}

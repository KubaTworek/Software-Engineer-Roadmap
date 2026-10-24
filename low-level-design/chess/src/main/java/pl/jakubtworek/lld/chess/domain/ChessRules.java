package pl.jakubtworek.lld.chess.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import static pl.jakubtworek.lld.chess.domain.IllegalMoveException.Reason.*;
import static pl.jakubtworek.lld.chess.domain.PieceType.*;

/**
 * Bezstanowa usługa domenowa: legalność wymaga współpracy całej pozycji, nie
 * samej figury. Rozdziela geometrię ataku od pełnego ruchu, dzięki czemu nie
 * wywołuje rekurencyjnie „legalne ruchy przeciwnika” podczas badania szacha.
 * Nawet figura związana (jej odejście odsłoniłoby króla) atakuje swoje pola.
 */
public final class ChessRules {

    private static final int[][] KNIGHT_JUMPS = { { 1, 2 }, { 2, 1 }, { 2, -1 }, { 1, -2 }, { -1, -2 }, { -2, -1 },
            { -2, 1 }, { -1, 2 } };
    private final MoveGenerator generator = new MoveGenerator();

    /**
     * Najpierw walidujemy intencję, potem budujemy kandydat i sprawdzamy króla.
     * Każda odmowa porzuca lokalną wartość; żaden krok nie zmienia wejściowej
     * pozycji. Metoda nie sprawdza końca partii: to kontrakt ChessGame.
     */
    public Position after(Position position, Move move) {
        return switch(evaluate(position, move)) {
            case MoveEvaluation.Accepted accepted ->
                accepted.position();
            case MoveEvaluation.Rejected rejected ->
                throw new IllegalMoveException(rejected.reason());
        };
    }

    /**
     * Wspólna walidacja dla komendy, generatora, perft i tożsamości pozycji.
     */
    public MoveEvaluation evaluate(Position position, Move move) {
        Objects.requireNonNull(position);
        Objects.requireNonNull(move);
        Board board = position.board();
        if (move.from().equals(move.to()))
            return new MoveEvaluation.Rejected(SAME_SQUARE);
        Piece piece = board.at(move.from()).orElse(null);
        if (piece == null)
            return new MoveEvaluation.Rejected(EMPTY_SOURCE);
        if (piece.color() != position.turn())
            return new MoveEvaluation.Rejected(WRONG_TURN);
        Piece target = board.at(move.to()).orElse(null);
        if (target != null && target.color() == piece.color())
            return new MoveEvaluation.Rejected(OWN_PIECE);
        if (target != null && target.type() == KING)
            return new MoveEvaluation.Rejected(KING_CAPTURE);
        boolean promotion = piece.type() == PAWN && move.to().rank() == piece.color().promotionRank();
        if (promotion && move.promotion() == null)
            return new MoveEvaluation.Rejected(PROMOTION_REQUIRED);
        if (!promotion && move.promotion() != null)
            return new MoveEvaluation.Rejected(UNEXPECTED_PROMOTION);
        int dx = move.to().file() - move.from().file();
        int dy = move.to().rank() - move.from().rank();
        boolean castling = piece.type() == KING && dy == 0 && Math.abs(dx) == 2;
        boolean enPassant = false;
        if (castling) {
            var rejection = validateCastling(position, move, piece);
            if (rejection != null)
                return new MoveEvaluation.Rejected(rejection);
        } else if (piece.type() == PAWN) {
            var rejection = validatePawn(position, move, piece, target);
            if (rejection != null)
                return new MoveEvaluation.Rejected(rejection);
            enPassant = dx != 0 && target == null;
        } else {
            if (!geometry(piece.type(), dx, dy))
                return new MoveEvaluation.Rejected(ILLEGAL_GEOMETRY);
            if (piece.type() != KNIGHT && !clearPath(board, move.from(), move.to())) {
                return new MoveEvaluation.Rejected(BLOCKED);
            }
        }
        var removed = new ArrayList<Square>(List.of(move.from(), move.to()));
        var placed = new HashMap<Square, Piece>();
        placed.put(move.to(), promotion ? new Piece(piece.color(), move.promotion()) : piece);
        if (enPassant) {
            // Bity pion nie stoi na polu docelowym. Usuwamy go PRZED kontrolą
            // szacha; np. e5xd6 może odsłonić linię wieży do własnego króla.
            removed.add(new Square(move.to().file(), move.from().rank()));
        }
        if (castling) {
            boolean kingSide = dx > 0;
            Square rookFrom = new Square(kingSide ? 7 : 0, piece.color().homeRank());
            removed.add(rookFrom);
            placed.put(new Square(kingSide ? 5 : 3, piece.color().homeRank()), new Piece(piece.color(), ROOK));
        }
        Board candidate = board.changed(removed, placed);
        if (inCheck(candidate, piece.color()))
            return new MoveEvaluation.Rejected(KING_IN_CHECK);
        Square ep = piece.type() == PAWN && Math.abs(dy) == 2 ? new Square(move.from().file(), (move.from()
                .rank() + move.to().rank()) / 2) : null;
        // Każdy poprawny ruch ustala nowe okno en passant lub je usuwa.
        // Odrzucona komenda nie zużywa tury i nie wygasza poprzedniego okna.
        // Bicie w przelocie też zeruje licznik, choć cel przed ruchem był pusty.
        long halfmove = piece.type() == PAWN || target != null ? 0 : increment(position.halfmoveClock());
        long fullmove = piece.color() == Color.BLACK ? increment(position.fullmoveNumber()) : position.fullmoveNumber();
        return new MoveEvaluation.Accepted(new Position(candidate, piece.color().opposite(), position.rights()
                .after(move, piece), ep, halfmove, fullmove));
    }

    private IllegalMoveException.Reason validatePawn(Position position, Move move, Piece piece, Piece target) {
        int dx = move.to().file() - move.from().file();
        int dy = move.to().rank() - move.from().rank();
        int forward = piece.color().forward();
        if (dx == 0 && target == null) {
            if (dy == forward)
                return null;
            if (dy == 2 * forward && move.from().rank() == piece.color().pawnRank()) {
                if (position.board().at(new Square(move.from().file(), move.from().rank() + forward)).isPresent()) {
                    return BLOCKED;
                }
                return null;
            }
        }
        if (Math.abs(dx) == 1 && dy == forward) {
            if (target != null)
                return null;
            if (position.enPassantTarget().filter(move.to()::equals).isPresent()) {
                Piece victim = position.board().at(new Square(move.to().file(), move.from().rank())).orElse(null);
                if (new Piece(piece.color().opposite(), PAWN).equals(victim))
                    return null;
            }
        }
        return ILLEGAL_GEOMETRY;
    }

    private IllegalMoveException.Reason validateCastling(Position position, Move move, Piece king) {
        int rank = king.color().homeRank();
        boolean kingSide = move.to().file() > move.from().file();
        Square rook = new Square(kingSide ? 7 : 0, rank);
        if (!move.from().equals(new Square(4, rank)) || !move.to().equals(new Square(kingSide ? 6 : 2, rank))
                || !position.rights().allows(king.color(), kingSide) || !position.board().at(rook)
                .filter(new Piece(king.color(), ROOK)::equals).isPresent() || !clearPath(position.board(), move.from(),
                rook) || inCheck(position.board(), king.color())) {
            return CASTLING_FORBIDDEN;
        }
        Square transit = new Square(kingSide ? 5 : 3, rank);
        // Badamy króla również na polu pośrednim, po usunięciu go ze źródła.
        // Bez symulacji odsłonięta linia ataku mogłaby zostać przeoczona.
        Board middle = position.board().changed(List.of(move.from()), Map.of(transit, king));
        if (inCheck(middle, king.color()))
            return CASTLING_FORBIDDEN;
        // Pole docelowe sprawdza wspólna kontrola kandydata już z przesuniętą wieżą.
        return null;
    }

    /**
     * Atak nie oznacza „przeciwnik może teraz legalnie zagrać”: pion kontroluje
     * puste skosy, król sąsiednie pola, a związanie nie usuwa ataku. Nie sprawdzamy
     * tury ani koloru figury na celu. Koniec promienia może być bronioną figurą.
     */
    public boolean attacked(Board board, Square square, Color by) {
        Objects.requireNonNull(board);
        Objects.requireNonNull(square);
        Objects.requireNonNull(by);
        int file = square.file(), rank = square.rank();
        // Badamy atak OD celu: osiem promieni zatrzymuje się na pierwszej
        // figurze. Nie przeglądamy wszystkich figur i nie badamy osobno ich dróg.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                for (int distance = 1, x = file + dx, y = rank + dy; x >= 0 && x < 8 && y >= 0 && y < 8; distance++,
                        x += dx, y += dy) {
                    Piece piece = board.at(x, y);
                    if (piece == null) {
                        continue;
                    }
                    if (piece.color() == by) {
                        PieceType type = piece.type();
                        boolean diagonal = dx != 0 && dy != 0;
                        if (type == QUEEN || type == BISHOP && diagonal || type == ROOK && !diagonal || distance == 1
                                && (type == KING || type == PAWN && diagonal && dy == -by.forward())) {
                            return true;
                        }
                    }
                    break;
                }
            }
        }
        int[][] jumps = KNIGHT_JUMPS;
        for (int[] jump : jumps) {
            int x = file + jump[0], y = rank + jump[1];
            if (x >= 0 && x < 8 && y >= 0 && y < 8) {
                Piece piece = board.at(x, y);
                if (piece != null && piece.color() == by && piece.type() == KNIGHT) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean inCheck(Board board, Color color) {
        return attacked(board, board.king(color), color.opposite());
    }

    /**
     * Generator ogranicza kandydatów geometrią. Wspólne evaluate rozstrzyga
     * bezpieczeństwo króla i prawa specjalne bez wyjątków dla zwykłej odmowy.
     * Nadal kopiujemy plansze; to ćwiczenie poprawności, nie silnik AI.
     */
    public List<Move> legalMoves(Position position) {
        var result = new ArrayList<Move>();
        for (Move move : generator.candidates(position)) {
            if (evaluate(position, move) instanceof MoveEvaluation.Accepted)
                result.add(move);
        }
        return List.copyOf(result);
    }

    /**
     * Nie buduje pełnej listy legalnych ruchów: kończy ocenę po pierwszym sukcesie.
     */
    public boolean hasLegalMove(Position position) {
        for (Move move : generator.candidates(position)) {
            if (evaluate(position, move) instanceof MoveEvaluation.Accepted)
                return true;
        }
        return false;
    }

    public GameStatus status(Position position) {
        boolean check = inCheck(position.board(), position.turn());
        if (!hasLegalMove(position))
            return check ? GameStatus.CHECKMATE : GameStatus.STALEMATE;
        return check ? GameStatus.CHECK : GameStatus.ONGOING;
    }

    private boolean geometry(PieceType type, int dx, int dy) {
        int x = Math.abs(dx), y = Math.abs(dy);
        if (x == 0 && y == 0)
            return false;
        return switch(type) {
            case KING ->
                Math.max(x, y) == 1;
            case QUEEN ->
                x == y || x == 0 || y == 0;
            case ROOK ->
                x == 0 || y == 0;
            case BISHOP ->
                x == y;
            case KNIGHT ->
                x * y == 2;
            // Pion ma inną geometrię ataku i ruchu; obsługujemy go jawnie.
            case PAWN ->
                false;
        };
    }

    private long increment(long value) {
        try {
            return Math.incrementExact(value);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Przekroczono zakres licznika pozycji", ex);
        }
    }

    private boolean clearPath(Board board, Square from, Square to) {
        int stepX = Integer.signum(to.file() - from.file());
        int stepY = Integer.signum(to.rank() - from.rank());
        int file = from.file() + stepX, rank = from.rank() + stepY;
        // Wywołujący potwierdził linię prostą/skos. Cel pomijamy: bicie na końcu
        // jest dozwolone, przeskakiwanie wcześniejszej figury już nie.
        while (file != to.file() || rank != to.rank()) {
            if (board.at(new Square(file, rank)).isPresent())
                return false;
            file += stepX;
            rank += stepY;
        }
        return true;
    }
}

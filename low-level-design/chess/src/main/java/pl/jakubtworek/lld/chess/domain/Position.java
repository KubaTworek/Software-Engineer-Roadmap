package pl.jakubtworek.lld.chess.domain;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Snapshot obejmuje planszę i kontekst legalności. Dwie identyczne plansze mogą
 * mieć inne ruchy: po powrocie wieży nie ma roszady, po innym ruchu znika en passant.
 * Konstruktor pakietowy ogranicza wytwarzanie kontekstu do reguł, pozycji startowej
 * i walidowanego adaptera FEN. Półruchy od ostatniego bicia/ruchu piona służą
 * progom remisów; numer pełnego ruchu rośnie dopiero po ruchu czarnych.
 */
public final class Position {

    private final Board board;
    private final Color turn;
    private final CastlingRights rights;
    private final Square enPassantTarget;
    private final long halfmoveClock;
    private final long fullmoveNumber;

    Position(Board board, Color turn, CastlingRights rights, Square enPassantTarget) {
        this(board, turn, rights, enPassantTarget, 0, 1);
    }

    /**
     * Liczniki opisują pozycję; nie zastępują historii potrzebnej do powtórzeń.
     */
    Position(Board board, Color turn, CastlingRights rights, Square enPassantTarget, long halfmoveClock,
            long fullmoveNumber) {
        this.board = Objects.requireNonNull(board);
        this.turn = Objects.requireNonNull(turn);
        this.rights = Objects.requireNonNull(rights);
        this.enPassantTarget = enPassantTarget;
        if (halfmoveClock < 0 || fullmoveNumber < 1)
            throw new IllegalArgumentException("Niepoprawne liczniki pozycji");
        this.halfmoveClock = halfmoveClock;
        this.fullmoveNumber = fullmoveNumber;
        for (Color color : Color.values()) {
            long kings = Long.bitCount(board.mask(color, PieceType.KING));
            if (kings != 1)
                throw new IllegalArgumentException("Wymagany dokładnie jeden król: " + color);
        }
        if (((board.mask(Color.WHITE, PieceType.PAWN) | board.mask(Color.BLACK,
                PieceType.PAWN)) & 0xff000000000000ffL) != 0) {
            throw new IllegalArgumentException("Pion na ostatniej linii wymaga promocji");
        }
    }

    public static Position initial() {
        return new Position(Board.initial(), Color.WHITE, CastlingRights.initial(), null);
    }

    /**
     * Pozycja do analizy/testów, bez domyślnych praw historycznych. Sprawdzamy
     * strukturę, nie dowodzimy osiągalności w legalnej partii (np. liczb bić).
     * Jest to świadoma granica API, a nie importer pełnego stanu FEN.
     */
    public static Position analysis(Map<Square, Piece> pieces, Color turn) {
        return new Position(new Board(pieces), turn, CastlingRights.none(), null);
    }

    public Board board() {
        return board;
    }

    public Color turn() {
        return turn;
    }

    public CastlingRights rights() {
        return rights;
    }

    public Optional<Square> enPassantTarget() {
        return Optional.ofNullable(enPassantTarget);
    }

    public long halfmoveClock() {
        return halfmoveClock;
    }

    public long fullmoveNumber() {
        return fullmoveNumber;
    }
}

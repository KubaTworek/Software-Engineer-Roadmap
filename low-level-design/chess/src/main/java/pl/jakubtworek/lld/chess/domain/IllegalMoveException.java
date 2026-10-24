package pl.jakubtworek.lld.chess.domain;

/**
 * Kod pozwala testować kontrakt odmowy bez uzależnienia od tekstu prezentacji.
 */
public final class IllegalMoveException extends IllegalArgumentException {

    public enum Reason {

        GAME_OVER,
        SAME_SQUARE,
        EMPTY_SOURCE,
        WRONG_TURN,
        OWN_PIECE,
        KING_CAPTURE,
        PROMOTION_REQUIRED,
        UNEXPECTED_PROMOTION,
        ILLEGAL_GEOMETRY,
        BLOCKED,
        CASTLING_FORBIDDEN,
        KING_IN_CHECK
    }

    private final Reason reason;

    public IllegalMoveException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}

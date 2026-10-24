package pl.jakubtworek.lld.chess.domain;

/**
 * Zamknięty zbiór rodzajów figur; promocja nie może tworzyć króla ani piona.
 */
public enum PieceType {

    KING('k'),
    QUEEN('q'),
    ROOK('r'),
    BISHOP('b'),
    KNIGHT('n'),
    PAWN('p');

    private final char symbol;

    PieceType(char symbol) {
        this.symbol = symbol;
    }

    public char symbol() {
        return symbol;
    }

    public boolean canPromoteTo() {
        return this != KING && this != PAWN;
    }
}

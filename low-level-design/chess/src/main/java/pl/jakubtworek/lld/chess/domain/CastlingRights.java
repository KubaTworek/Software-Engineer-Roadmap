package pl.jakubtworek.lld.chess.domain;

/**
 * Cztery historyczne uprawnienia. Powrót wieży lub króla na pole początkowe nie
 * odtwarza prawa roszady. Bicie wieży na jej polu też nie może zostawić prawa
 * dla przyszłej wieży po promocji. To informacja, której nie da się odczytać z Board.
 */
public record CastlingRights(boolean whiteKingSide, boolean whiteQueenSide, boolean blackKingSide,
        boolean blackQueenSide) {

    public static CastlingRights none() {
        return new CastlingRights(false, false, false, false);
    }

    public static CastlingRights initial() {
        return new CastlingRights(true, true, true, true);
    }

    public boolean allows(Color color, boolean kingSide) {
        return color == Color.WHITE ? (kingSide ? whiteKingSide : whiteQueenSide) : (kingSide ? blackKingSide : blackQueenSide);
    }

    CastlingRights after(Move move, Piece piece) {
        boolean wk = whiteKingSide, wq = whiteQueenSide, bk = blackKingSide, bq = blackQueenSide;
        if (piece.type() == PieceType.KING) {
            if (piece.color() == Color.WHITE) {
                wk = false;
                wq = false;
            } else {
                bk = false;
                bq = false;
            }
        }
        // Dotknięcie narożnika jako źródła lub celu usuwa prawo nieodwracalnie.
        // W osiągalnej pozycji z zachowanym prawem na narożniku jest pierwotna wieża.
        for (Square square : new Square[] { move.from(), move.to() }) {
            if (square.equals(Square.parse("h1")))
                wk = false;
            if (square.equals(Square.parse("a1")))
                wq = false;
            if (square.equals(Square.parse("h8")))
                bk = false;
            if (square.equals(Square.parse("a8")))
                bq = false;
        }
        return new CastlingRights(wk, wq, bk, bq);
    }
}

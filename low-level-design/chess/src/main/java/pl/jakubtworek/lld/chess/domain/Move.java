package pl.jakubtworek.lld.chess.domain;

import java.util.Objects;

/**
 * Intencja gracza, nie dowód legalności. Opcjonalna promocja jest częścią ruchu:
 * nie publikujemy przejściowego stanu z pionem stojącym na ostatniej linii.
 * null oznacza brak wyboru; dostępne rodzaje weryfikujemy niezależnie od pozycji.
 */
public record Move(Square from, Square to, PieceType promotion) {

    public Move {
        Objects.requireNonNull(from);
        Objects.requireNonNull(to);
        if (promotion != null && !promotion.canPromoteTo()) {
            throw new IllegalArgumentException("Promocja wymaga Q, R, B lub N");
        }
    }

    public Move(Square from, Square to) {
        this(from, to, null);
    }

    public static Move of(String from, String to) {
        return new Move(Square.parse(from), Square.parse(to));
    }

    public static Move promote(String from, String to, PieceType type) {
        return new Move(Square.parse(from), Square.parse(to), Objects.requireNonNull(type));
    }
}

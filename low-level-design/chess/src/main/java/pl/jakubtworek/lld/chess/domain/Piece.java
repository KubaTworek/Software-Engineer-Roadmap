package pl.jakubtworek.lld.chess.domain;

import java.util.Objects;

/**
 * Wartość opisująca figurę, bez tożsamości i setterów. Ruch nie zmienia figury:
 * zmienia jej przypisanie do pola. Wspólna geometria i zależności między figurami
 * należą do ChessRules, dlatego nie tworzymy sześciu pustych podklas.
 */
public record Piece(Color color, PieceType type) {

    public Piece {
        Objects.requireNonNull(color);
        Objects.requireNonNull(type);
    }

    public char symbol() {
        return color == Color.WHITE ? Character.toUpperCase(type.symbol()) : type.symbol();
    }
}

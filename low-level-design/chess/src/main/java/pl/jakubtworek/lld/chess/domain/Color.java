package pl.jakubtworek.lld.chess.domain;

/**
 * Kolor określa turę i kierunek pionów. Rząd 0 oznacza pierwszą linię białych.
 */
public enum Color {

    WHITE(1), BLACK(-1);

    private final int forward;

    Color(int forward) {
        this.forward = forward;
    }

    public int forward() {
        return forward;
    }

    public int homeRank() {
        return this == WHITE ? 0 : 7;
    }

    public int pawnRank() {
        return this == WHITE ? 1 : 6;
    }

    public int promotionRank() {
        return this == WHITE ? 7 : 0;
    }

    public Color opposite() {
        return this == WHITE ? BLACK : WHITE;
    }
}

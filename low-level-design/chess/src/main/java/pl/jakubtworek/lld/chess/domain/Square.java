package pl.jakubtworek.lld.chess.domain;

/**
 * Value object pola: walidacja granic odbywa się raz przy konstrukcji.
 * Wewnętrzne współrzędne 0..7 nie wyciekają do protokołu konsoli a1..h8.
 */
public record Square(int file, int rank) {

    public Square {
        if (file < 0 || file > 7 || rank < 0 || rank > 7) {
            throw new IllegalArgumentException("Pole musi należeć do planszy 8 × 8");
        }
    }

    public static Square parse(String text) {
        if (text == null || !text.matches("[a-h][1-8]")) {
            throw new IllegalArgumentException("Oczekiwano pola a1..h8");
        }
        return new Square(text.charAt(0) - 'a', text.charAt(1) - '1');
    }

    @Override
    public String toString() {
        return "" + (char) ('a' + file) + (rank + 1);
    }
}

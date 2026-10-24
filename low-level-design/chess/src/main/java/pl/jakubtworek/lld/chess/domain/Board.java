package pl.jakubtworek.lld.chess.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Tablica 64 pól daje bezpośrednie adresowanie bez hashowania Square.
 * Maski long dla koloru i rodzaju figury pozwalają zliczać i wybierać króla
 * instrukcjami bitowymi. Mapa jest leniwym adapterem publicznego API.
 * Ruch kopiuje stałe 64 referencje; nie buduje HashMap dla każdej symulacji.
 */
public final class Board {

    private final Piece[] cells;
    private final long[][] masks;

    // Plansza jest logicznie stała, lecz ten leniwy cache zapisujemy przy
    // odczycie. Nie wynika z tego kontrakt synchronizacji między wątkami.
    private Map<Square, Piece> view;

    public Board(Map<Square, Piece> pieces) {
        cells = new Piece[64];
        Map.copyOf(pieces).forEach((square, piece) -> cells[index(square)] = piece);
        masks = indexMasks(cells);
    }

    private Board(Piece[] cells) {
        this.cells = cells;
        masks = indexMasks(cells);
    }

    public Map<Square, Piece> pieces() {
        if (view == null) {
            var result = new HashMap<Square, Piece>();
            for (int i = 0; i < 64; i++) {
                if (cells[i] != null) {
                    result.put(new Square(i & 7, i >>> 3), cells[i]);
                }
            }
            view = Map.copyOf(result);
        }
        return view;
    }

    public Optional<Piece> at(Square square) {
        return Optional.ofNullable(cells[index(Objects.requireNonNull(square))]);
    }

    Piece at(int file, int rank) {
        return cells[rank * 8 + file];
    }

    long mask(Color color, PieceType type) {
        return masks[color.ordinal()][type.ordinal()];
    }

    public Square king(Color color) {
        long kings = mask(color, PieceType.KING);
        if (kings == 0) {
            throw new IllegalArgumentException("Brak króla: " + color);
        }
        int square = Long.numberOfTrailingZeros(kings);
        return new Square(square & 7, square >>> 3);
    }

    Board changed(Iterable<Square> removed, Map<Square, Piece> placed) {
        // clone kopiuje tablicę referencji. Figury są niemutowalne, więc
        // współdzielenie ich obiektów nie zmieni zawartości starej planszy.
        Piece[] next = cells.clone();
        removed.forEach(square -> next[index(square)] = null);
        placed.forEach((square, piece) -> next[index(square)] = piece);
        return new Board(next);
    }

    private static int index(Square square) {
        return square.rank() * 8 + square.file();
    }

    private static long[][] indexMasks(Piece[] cells) {
        long[][] result = new long[2][PieceType.values().length];
        for (int i = 0; i < 64; i++) {
            Piece piece = cells[i];
            if (piece != null) {
                // L wymusza 64 bity. Dla pola 63 long jest ujemny, ale nadal
                // stanowi poprawną maskę; niepustość sprawdzamy przez != 0.
                result[piece.color().ordinal()][piece.type().ordinal()] |= 1L << i;
            }
        }
        return result;
    }

    static Board initial() {
        var pieces = new HashMap<Square, Piece>();
        PieceType[] back = { PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN, PieceType.KING,
                PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK };
        for (Color color : Color.values()) {
            for (int file = 0; file < 8; file++) {
                pieces.put(new Square(file, color.homeRank()), new Piece(color, back[file]));
                pieces.put(new Square(file, color.pawnRank()), new Piece(color, PieceType.PAWN));
            }
        }
        return new Board(pieces);
    }
}

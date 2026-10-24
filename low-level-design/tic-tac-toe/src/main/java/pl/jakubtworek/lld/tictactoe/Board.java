package pl.jakubtworek.lld.tictactoe;

import java.util.Objects;
import java.util.Optional;
import static pl.jakubtworek.lld.tictactoe.IllegalMoveException.Reason.*;

/**
 * Bitowa plansza z trwałym drzewem słów 64-bitowych. Liść ma dwie maski X/O;
 * pustych gałęzi nie alokujemy. Klasyczne 3×3 mieści się w jednym liściu.
 * Ruch kopiuje drogę do słowa: O(log(W+1)) węzłów zamiast tablicy N wierszy,
 * W=ceil(N*N/64). Stare korzenie pozostają poprawne bez klonowania planszy.
 * Ceną jest O(log(W+1)) odczytu; skan wygranej nie alokuje Position/Optional.
 */
public final class Board {

    private record Node(Node left, Node right, long x, long o) {
    }

    private final int size, occupied, depth;
    private final Node root;

    private Board(int size, int occupied, int depth, Node root) {
        this.size = size;
        this.occupied = occupied;
        this.depth = depth;
        this.root = root;
    }

    static Board empty(int size) {
        // GameRules ogranicza N do 100. Liczba bitów potrzebnych na words-1
        // daje ceil(log2(words)) bez pętli i bez logarytmu zmiennoprzecinkowego.
        int words = (size * size + 63) >>> 6;
        int depth = 32 - Integer.numberOfLeadingZeros(words - 1);
        return new Board(size, 0, depth, null);
    }

    public int size() {
        return size;
    }

    public Optional<Mark> markAt(Position position) {
        requireInside(position);
        return Optional.ofNullable(markAtUnchecked(position.row(), position.column()));
    }

    public boolean isFull() {
        return occupied == size * size;
    }

    Board afterMove(Mark mark, Position position) {
        Objects.requireNonNull(mark);
        requireInside(position);
        if (markAtUnchecked(position.row(), position.column()) != null) {
            throw new IllegalMoveException(OCCUPIED, "Wybrane pole jest już zajęte");
        }
        int index = position.row() * size + position.column();
        return new Board(size, occupied + 1, depth, put(root, index >>> 6, 1L << (index & 63), mark, depth));
    }

    private static Node put(Node node, int word, long bit, Mark mark, int level) {
        if (level == 0) {
            long x = node == null ? 0 : node.x();
            long o = node == null ? 0 : node.o();
            return new Node(null, null, mark == Mark.X ? x | bit : x, mark == Mark.O ? o | bit : o);
        }
        Node left = node == null ? null : node.left();
        Node right = node == null ? null : node.right();
        // Bit numeru słowa wybiera gałąź. Niezmieniony brat pozostaje wspólny
        // ze starym korzeniem; tylko jedna ścieżka dostaje nowe węzły.
        if (((word >>> (level - 1)) & 1) == 0) {
            left = put(left, word, bit, mark, level - 1);
        } else {
            right = put(right, word, bit, mark, level - 1);
        }
        return new Node(left, right, 0, 0);
    }

    Mark markAtUnchecked(int row, int column) {
        int index = row * size + column;
        int word = index >>> 6;
        Node node = root;
        for (int level = depth; node != null && level > 0; level--) {
            node = ((word >>> (level - 1)) & 1) == 0 ? node.left() : node.right();
        }
        if (node == null) {
            return null;
        }
        // Przesunięcie long maskuje dystans do 6 bitów. L jest konieczne,
        // a bit znaku też jest poprawnym polem: sprawdzamy != 0, nie > 0.
        long bit = 1L << (index & 63);
        return (node.x() & bit) != 0 ? Mark.X : (node.o() & bit) != 0 ? Mark.O : null;
    }

    /**
     * Strukturalny koszt aktualizacji wynika z jednego nowego węzła na poziom.
     */
    int copiedNodesPerMove() {
        return depth + 1;
    }

    boolean contains(int row, int column) {
        return row >= 0 && row < size && column >= 0 && column < size;
    }

    private void requireInside(Position position) {
        Objects.requireNonNull(position);
        if (!contains(position.row(), position.column())) {
            throw new IllegalMoveException(OUT_OF_BOUNDS, "Pozycja znajduje się poza planszą");
        }
    }
}

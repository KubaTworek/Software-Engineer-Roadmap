package pl.jakubtworek.lld.chess.domain;

import java.util.*;

/**
 * Trwała lista jednokierunkowa od ostatniego ruchu do pierwszego.
 * Append i getLast mają O(1), dawne wersje współdzielą ogon.
 * Chronologiczny iterator odwraca referencje w pomocniczej tablicy O(H).
 * To wybór pod dopisywanie/cofanie; losowy dostęp get(i) ma O(H), nie O(1).
 */
final class MoveHistory extends AbstractList<Move> {

    private final Move last;
    private final MoveHistory previous;
    private final int count;

    private MoveHistory(Move last, MoveHistory previous, int count) {
        this.last = last;
        this.previous = previous;
        this.count = count;
    }

    static MoveHistory copyOf(List<Move> source) {
        if (source instanceof MoveHistory history) {
            return history;
        }
        var history = new MoveHistory(null, null, 0);
        for (Move move : source) {
            history = history.append(move);
        }
        return history;
    }

    MoveHistory append(Move move) {
        return new MoveHistory(Objects.requireNonNull(move), this, Math.incrementExact(count));
    }

    @Override
    public int size() {
        return count;
    }

    @Override
    public Move getLast() {
        if (count == 0) {
            throw new NoSuchElementException();
        }
        return last;
    }

    @Override
    public Move get(int index) {
        Objects.checkIndex(index, count);
        MoveHistory node = this;
        for (int i = count - 1; i > index; i--) {
            node = node.previous;
        }
        return node.last;
    }

    @Override
    public Iterator<Move> iterator() {
        Move[] chronological = new Move[count];
        MoveHistory node = this;
        for (int i = count - 1; i >= 0; i--) {
            chronological[i] = node.last;
            node = node.previous;
        }
        return Collections.unmodifiableList(Arrays.asList(chronological)).iterator();
    }

    /**
     * AbstractList domyślnie iteruje przez get(i). Materializacja raz chroni
     * equals/listIterator przed kwadratowym kosztem dla listy wiązanej.
     */
    @Override
    public java.util.ListIterator<Move> listIterator(int index) {
        return java.util.Collections.unmodifiableList(new java.util.ArrayList<>(this)).listIterator(index);
    }
}

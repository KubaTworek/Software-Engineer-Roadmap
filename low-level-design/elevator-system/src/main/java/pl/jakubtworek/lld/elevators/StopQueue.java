package pl.jakubtworek.lld.elevators;

import java.util.AbstractList;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Trwałe FIFO: dwa jednokierunkowe stosy współdzielą ogony między snapshotami.
 * Dopisanie nie kopiuje Q przystanków. Odwrócenie tylnego stosu kosztuje O(Q),
 * ale na liniowej historii każde ogniwo odwracamy najwyżej raz: amortyzowane O(1).
 * Rozgałęzianie dawnych wersji może powtórzyć ten koszt; nie obiecujemy wtedy amortyzacji.
 * Osobny binarny trie 32 bitów daje contains/add/remove w stałych 32 krokach,
 * także dla ujemnych pięter. Nie alokujemy tablicy rozmiaru całego budynku.
 */
final class StopQueue extends AbstractList<Integer> {

    // Węzeł trzyma int bez boxingu; granica List<Integer> boksuje dopiero
    // odczyt. Finalne ogniwa można współdzielić między korzeniami kolejek.
    private record Link(int value, Link next) {
    }

    private record Bits(Bits zero, Bits one) {
    }

    private final Link front, back;
    private final Bits members;
    private final int count;

    private StopQueue(Link front, Link back, Bits members, int count) {
        this.front = front;
        this.back = back;
        this.members = members;
        this.count = count;
    }

    static StopQueue empty() {
        return new StopQueue(null, null, null, 0);
    }

    StopQueue append(int floor) {
        return count == 0 ? new StopQueue(new Link(floor, null), null, put(members, floor, 31, true),
                1) : new StopQueue(front, new Link(floor, back), put(members, floor, 31, true), count + 1);
    }

    StopQueue tail() {
        if (front == null) {
            throw new NoSuchElementException();
        }
        Link next = front.next;
        Link rear = back;
        if (next == null) {
            while (rear != null) {
                next = new Link(rear.value, next);
                rear = rear.next;
            }
        }
        return new StopQueue(next, next == front.next ? back : null, put(members, front.value, 31, false), count - 1);
    }

    @Override
    public int size() {
        return count;
    }

    @Override
    public Integer getFirst() {
        if (front == null) {
            throw new NoSuchElementException();
        }
        return front.value;
    }

    @Override
    public Integer get(int index) {
        java.util.Objects.checkIndex(index, count);
        var iterator = iterator();
        while (index-- > 0) {
            iterator.next();
        }
        return iterator.next();
    }

    @Override
    public boolean contains(Object value) {
        if (!(value instanceof Integer floor)) {
            return false;
        }
        Bits node = members;
        for (int bit = 31; bit >= 0 && node != null; bit--) {
            node = (floor & (1 << bit)) == 0 ? node.zero : node.one;
        }
        return node != null;
    }

    @Override
    public Iterator<Integer> iterator() {
        // Własne przejście odwiedza ogniwa raz. Pętla z get(i) na tej
        // reprezentacji powtarzałaby dojście od początku i kosztowałaby O(Q²).
        return new Iterator<>() {

            Link head = front;
            ArrayDeque<Integer> reversed;

            public boolean hasNext() {
                return head != null || back != null && (reversed == null || !reversed.isEmpty());
            }

            public Integer next() {
                if (head != null) {
                    int value = head.value;
                    head = head.next;
                    return value;
                }
                // Pomocniczy stos powstaje dopiero po wyczerpaniu frontu;
                // iterator nie modyfikuje ogniw żadnego zachowanego snapshotu.
                if (reversed == null) {
                    reversed = new ArrayDeque<>();
                    for (Link node = back; node != null; node = node.next) {
                        reversed.push(node.value);
                    }
                }
                if (reversed.isEmpty()) {
                    throw new NoSuchElementException();
                }
                return reversed.pop();
            }
        };
    }

    private static Bits put(Bits node, int value, int bit, boolean present) {
        if (bit < 0) {
            return present ? new Bits(null, null) : null;
        }
        Bits zero = node == null ? null : node.zero, one = node == null ? null : node.one;
        if ((value & (1 << bit)) == 0) {
            zero = put(zero, value, bit - 1, present);
        } else {
            one = put(one, value, bit - 1, present);
        }
        return zero == null && one == null ? null : new Bits(zero, one);
    }

    /**
     * AbstractList domyślnie iteruje przez get(i). Materializacja raz chroni
     * equals/listIterator przed kwadratowym kosztem dla listy wiązanej.
     */
    @Override
    public java.util.ListIterator<Integer> listIterator(int index) {
        return java.util.Collections.unmodifiableList(new java.util.ArrayList<>(this)).listIterator(index);
    }
}

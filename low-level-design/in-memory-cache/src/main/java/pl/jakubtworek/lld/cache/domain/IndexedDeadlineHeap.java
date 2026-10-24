package pl.jakubtworek.lld.cache.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Kopiec binarny minimum z indeksem klucz -> pozycja. Standardowa PriorityQueue
 * usuwa wskazany obiekt liniowo; tu zamiana z ostatnim i przesiewanie kosztują
 * O(log N). Każda zamiana MUSI poprawić oba wpisy indeksu, inaczej kolejny TTL
 * usunąłby inny klucz. Nie odkładamy martwych biletów i nie potrzebujemy kompaktowania.
 */
final class IndexedDeadlineHeap<K, T> {

    private final ArrayList<T> heap = new ArrayList<>();

    // Indeks boksuje pozycję w Integer, ale eliminuje liniowe remove(Object)
    // ze zwykłej PriorityQueue. Klucz musi mieć stabilne equals/hashCode.
    private final Map<K, Integer> positions = new HashMap<>();
    private final Comparator<T> order;
    private final Function<T, K> key;

    IndexedDeadlineHeap(Comparator<T> order, Function<T, K> key) {
        this.order = order;
        this.key = key;
    }

    int size() {
        return heap.size();
    }

    boolean isEmpty() {
        return heap.isEmpty();
    }

    T peek() {
        return heap.getFirst();
    }

    void clear() {
        heap.clear();
        positions.clear();
    }

    void add(T value) {
        removeKey(key.apply(value));
        positions.put(key.apply(value), heap.size());
        heap.add(value);
        up(heap.size() - 1);
    }

    T poll() {
        T first = peek();
        removeKey(key.apply(first));
        return first;
    }

    void removeKey(K target) {
        Integer index = positions.remove(target);
        if (index == null) {
            return;
        }
        // Usuwamy koniec tablicy bez przesuwania pozostałych elementów.
        // Wstawienie go w lukę wymaga potem przesiewania i korekty indeksu.
        T last = heap.removeLast();
        if (index == heap.size()) {
            return;
        }
        heap.set(index, last);
        positions.put(key.apply(last), index);
        if (index > 0 && order.compare(last, heap.get((index - 1) >>> 1)) < 0) {
            up(index);
        } else {
            down(index);
        }
    }

    private void up(int index) {
        while (index > 0) {
            int parent = (index - 1) >>> 1;
            if (order.compare(heap.get(parent), heap.get(index)) <= 0) {
                break;
            }
            swap(parent, index);
            index = parent;
        }
    }

    private void down(int index) {
        // index < size/2 zabezpiecza także przed przepełnieniem 2*index+1.
        while (index < heap.size() / 2) {
            int child = index * 2 + 1;
            if (child + 1 < heap.size() && order.compare(heap.get(child + 1), heap.get(child)) < 0) {
                child++;
            }
            if (order.compare(heap.get(index), heap.get(child)) <= 0) {
                break;
            }
            swap(index, child);
            index = child;
        }
    }

    private void swap(int left, int right) {
        T a = heap.get(left), b = heap.get(right);
        heap.set(left, b);
        heap.set(right, a);
        positions.put(key.apply(b), left);
        positions.put(key.apply(a), right);
    }
}

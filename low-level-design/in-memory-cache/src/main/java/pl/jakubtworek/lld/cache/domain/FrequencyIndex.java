package pl.jakubtworek.lld.cache.domain;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * LFU w O(1) średnio: lista dwukierunkowa kubełków rosnących częstotliwości.
 * W kubełku LinkedHashSet utrzymuje kolejność LRU jako rozstrzygnięcie remisu.
 * Trafienie przesuwa klucz wyłącznie do kubełka f+1, więc nie szukamy miejsca
 * w całej liście. Puste kubełki odpinamy przez dwa wskaźniki.
 */
final class FrequencyIndex<K> {

    private static final class Bucket<K> {

        final long frequency;
        final LinkedHashSet<K> keys = new LinkedHashSet<>();
        Bucket<K> previous, next;

        Bucket(long frequency) {
            this.frequency = frequency;
        }
    }

    private final Map<K, Bucket<K>> locations = new HashMap<>();
    private Bucket<K> first;

    void add(K key) {
        if (first == null || first.frequency != 1) {
            insertAfter(null, new Bucket<>(1));
        }
        first.keys.add(key);
        locations.put(key, first);
    }

    void touch(K key) {
        Bucket<K> old = locations.get(key);
        // Nasycenie licznika nie zatrzymuje recency: remove + add przenosi
        // klucz na koniec LinkedHashSet również przy niezmienionej częstotliwości.
        if (old.frequency == Long.MAX_VALUE) {
            old.keys.remove(key);
            old.keys.add(key);
            return;
        }
        // Częstotliwość rośnie tylko o jeden, więc nowy kubełek może być
        // wyłącznie sąsiadem. Nie szukamy miejsca w całej liście kubełków.
        Bucket<K> target = old.next;
        if (target == null || target.frequency != old.frequency + 1) {
            target = new Bucket<>(old.frequency + 1);
            insertAfter(old, target);
        }
        old.keys.remove(key);
        target.keys.add(key);
        locations.put(key, target);
        if (old.keys.isEmpty()) {
            unlink(old);
        }
    }

    void remove(K key) {
        Bucket<K> bucket = locations.remove(key);
        if (bucket != null) {
            bucket.keys.remove(key);
            if (bucket.keys.isEmpty()) {
                unlink(bucket);
            }
        }
    }

    K victim() {
        if (first == null) {
            throw new IllegalStateException("Pusty indeks LFU");
        }
        return first.keys.getFirst();
    }

    void clear() {
        locations.clear();
        first = null;
    }

    private void insertAfter(Bucket<K> previous, Bucket<K> value) {
        value.previous = previous;
        value.next = previous == null ? first : previous.next;
        if (value.next != null) {
            value.next.previous = value;
        }
        if (previous == null) {
            first = value;
        } else {
            previous.next = value;
        }
    }

    private void unlink(Bucket<K> value) {
        if (value.previous == null) {
            first = value.next;
        } else {
            value.previous.next = value.next;
        }
        if (value.next != null) {
            value.next.previous = value.previous;
        }
    }
}

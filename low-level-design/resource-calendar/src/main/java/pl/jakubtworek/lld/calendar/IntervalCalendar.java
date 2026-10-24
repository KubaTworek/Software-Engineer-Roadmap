package pl.jakubtworek.lld.calendar;

import java.util.*;

/**
 * Wspólny indeks dla LocalDate (auta) oraz Instant (sale).
 * W jednym indeksie przechowujemy wyłącznie nieprzecinające się przedziały
 * półotwarte [start,end). To założenie pozwala zrezygnować z ogólnego interval
 * tree z maxEnd: z lewej może nachodzić tylko poprzednik początku zapytania.
 * TreeMap porządkuje początki, HashMap prowadzi od ID do starego przedziału.
 * Kontrakt jednowątkowy, klucze i wartości muszą być stabilnymi wartościami.
 */
public final class IntervalCalendar<T extends Comparable<? super T>, K, V> {

    private record Slot<T, K, V>(T start, T end, K id, V value) {
    }

    private final NavigableMap<T, Slot<T, K, V>> starts = new TreeMap<>();
    private final Map<K, Slot<T, K, V>> ids = new HashMap<>();

    /**
     * Zmiana własnego ID jest walidowana przed usunięciem starej blokady.
     * Odmowa kolizji nie gubi poprzedniego terminu. O(log N) pracy drzewa.
     */
    public void put(K id, T start, T end, V value) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(value);
        requireRange(start, end);
        if (firstConflict(start, end, id).isPresent()) {
            throw new IllegalArgumentException("Kolizja przedziałów");
        }
        Slot<T, K, V> next = new Slot<>(start, end, id, value);
        // ID jest kluczem hash, a czas kluczem drzewa. Oba indeksy zmieniamy
        // razem; wcześniejsza ocena kolizji nie jest blokadą wątkową.
        remove(id);
        starts.put(start, next);
        ids.put(id, next);
    }

    public void remove(K id) {
        Objects.requireNonNull(id);
        Slot<T, K, V> old = ids.remove(id);
        if (old != null) {
            starts.remove(old.start());
        }
    }

    public int size() {
        return ids.size();
    }

    /**
     * Pierwszy konflikt kończy wyszukiwanie po pierwszym wyniku O(log N).
     * excluded służy zmianie terminu; null oznacza brak pomijanego wpisu.
     */
    public Optional<V> firstConflict(T start, T end, K excluded) {
        requireRange(start, end);
        var iterator = candidates(start);
        while (iterator.hasNext()) {
            var slot = iterator.next();
            if (slot.start().compareTo(end) >= 0) {
                break;
            }
            if (!slot.id().equals(excluded) && overlaps(slot, start, end)) {
                return Optional.of(slot.value());
            }
        }
        return Optional.empty();
    }

    /**
     * Pełny raport O(log N + K) uwzględnia koszt K zwracanych elementów.
     * Iterator tailMap przechodzi kolejne węzły, zamiast wołać higherEntry
     * od korzenia dla każdego trafienia. Zwracana lista jest stabilną kopią.
     */
    public List<V> conflicts(T start, T end, K excluded) {
        requireRange(start, end);
        var result = new ArrayList<V>();
        var iterator = candidates(start);
        while (iterator.hasNext()) {
            var slot = iterator.next();
            if (slot.start().compareTo(end) >= 0) {
                break;
            }
            if (!slot.id().equals(excluded) && overlaps(slot, start, end)) {
                result.add(slot.value());
            }
        }
        return List.copyOf(result);
    }

    private Iterator<Slot<T, K, V>> candidates(T start) {
        var entry = starts.floorEntry(start);
        if (entry == null || entry.getValue().end().compareTo(start) <= 0) {
            entry = starts.ceilingEntry(start);
        }
        // tailMap to żywy widok, nie kopia. Iterator przechodzi następniki
        // bez wyszukiwania od korzenia; w trakcie nie zmieniamy mapy.
        return entry == null ? Collections.emptyIterator() : starts.tailMap(entry.getKey(), true).values().iterator();
    }

    private boolean overlaps(Slot<T, K, V> slot, T start, T end) {
        return slot.start().compareTo(end) < 0 && start.compareTo(slot.end()) < 0;
    }

    private void requireRange(T start, T end) {
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);
        if (start.compareTo(end) >= 0) {
            throw new IllegalArgumentException("Wymagane start < end");
        }
    }
}

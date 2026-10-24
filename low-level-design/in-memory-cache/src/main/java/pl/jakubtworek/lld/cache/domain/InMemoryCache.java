package pl.jakubtworek.lld.cache.domain;

import java.time.*;
import java.util.*;

/**
 * Jednowątkowy właściciel wpisów, TTL, statystyk i reguły pojemności.
 * LinkedHashMap utrzymuje LRU -> MRU bez automatycznego dotykania przy peek.
 * Polityka wybiera ofiarę, a indeks ważności działa niezależnie od tej kolejności.
 * Klucze muszą mieć stabilne equals/hashCode bez wyjątków/efektów ubocznych.
 * Nie obiecujemy rollback po błędach własnego klucza ani po wyczerpaniu pamięci.
 */
public final class InMemoryCache<K, V> implements Cache<K, V> {

    private static final class Entry<V> {

        final V value;
        final Long deadline;
        long frequency;

        Entry(V value, Long deadline, long frequency) {
            this.value = value;
            this.deadline = deadline;
            this.frequency = frequency;
        }
    }

    private record Ticket<K, V>(long deadline, K key, Entry<V> identity) {
    }

    private int capacity;
    private final EvictionPolicy policy;
    private final Ticker ticker;
    private final Clock clock;
    private long lastTick;
    private int timedEntries;
    private final FrequencyIndex<K> frequencies = new FrequencyIndex<>();

    // Insertion-order jest celowe: get mapy używany przez peek nie zmienia
    // kolejności. Wariant accessOrder=true naruszyłby ten kontrakt odczytu.
    private final LinkedHashMap<K, Entry<V>> entries = new LinkedHashMap<>();
    private final IndexedDeadlineHeap<K, Ticket<K,
            V>> deadlines = new IndexedDeadlineHeap<>(Comparator.comparingLong(Ticket::deadline), Ticket::key);
    private long hits, misses, expirations, evictions, invalidations, clearRemovals;

    public InMemoryCache(int capacity, Ticker ticker, Clock clock) {
        this(capacity, EvictionPolicy.LRU, ticker, clock);
    }

    public InMemoryCache(int capacity, EvictionPolicy policy, Ticker ticker, Clock clock) {
        validCapacity(capacity);
        this.capacity = capacity;
        this.policy = Objects.requireNonNull(policy);
        this.ticker = Objects.requireNonNull(ticker);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Optional<V> get(K key) {
        Objects.requireNonNull(key);
        purge(time());
        // Trafienie usuwa i ponownie wstawia wpis na koniec recency.
        // To zapis kilku indeksów, nie „odczyt”, który można wykonać bez ochrony.
        Entry<V> entry = entries.remove(key);
        if (entry == null) {
            misses = increment(misses);
            return Optional.empty();
        }
        entry.frequency = increment(entry.frequency);
        frequencies.touch(key);
        entries.put(key, entry);
        hits = increment(hits);
        return Optional.of(entry.value);
    }

    @Override
    public Optional<V> peek(K key) {
        Objects.requireNonNull(key);
        purge(time());
        Entry<V> entry = entries.get(key);
        return entry == null ? Optional.empty() : Optional.of(entry.value);
    }

    @Override
    public void put(K key, V value) {
        putInternal(key, value, null);
    }

    @Override
    public void put(K key, V value, Duration ttl) {
        Objects.requireNonNull(ttl);
        if (ttl.isZero() || ttl.isNegative())
            throw new IllegalArgumentException("TTL musi być dodatni");
        // Zakres Duration sprawdzamy przed czasem i mutacją.
        putInternal(key, value, ttl.toNanos());
    }

    private void putInternal(K key, V value, Long ttlNanos) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        long now = time();
        Long deadline = ttlNanos == null ? null : Math.addExact(now, ttlNanos);
        Entry<V> old = entries.get(key);
        long frequency = old == null || old.deadline != null && old.deadline <= now ? 1 : increment(old.frequency);
        var candidate = new Entry<>(value, deadline, frequency);
        // Przepełnienie terminu wystąpiło przed jakąkolwiek zmianą/statystyką.
        purge(now);
        if (entries.containsKey(key)) {
            frequencies.touch(key);
        } else {
            frequencies.add(key);
        }
        Entry<V> replaced = entries.remove(key);
        if (replaced != null && replaced.deadline != null) {
            timedEntries--;
        }
        deadlines.removeKey(key);
        entries.put(key, candidate);
        if (deadline != null) {
            timedEntries++;
            deadlines.add(new Ticket<>(deadline, key, candidate));
        }
        if (entries.size() > capacity)
            evict();
    }

    @Override
    public Optional<V> invalidate(K key) {
        Objects.requireNonNull(key);
        purge(time());
        Entry<V> removed = remove(key);
        if (removed != null)
            invalidations = increment(invalidations);
        return removed == null ? Optional.empty() : Optional.of(removed.value);
    }

    @Override
    public void clear() {
        clearRemovals = saturatedAdd(clearRemovals, entries.size());
        entries.clear();
        frequencies.clear();
        deadlines.clear();
        timedEntries = 0;
    }

    @Override
    public int size() {
        purge(time());
        return entries.size();
    }

    @Override
    public int cleanUp() {
        return purge(time()).size();
    }

    @Override
    public CacheSnapshot<K, V> snapshot() {
        // Clock jest etykietą. Jego awaria ma poprzedzać purge i statystyki.
        Instant observed = Objects.requireNonNull(clock.instant());
        long now = time();
        purge(now);
        var items = new ArrayList<CacheItem<K, V>>();
        entries.forEach((key, entry) -> items.add(new CacheItem<>(key, entry.value, entry.frequency,
                entry.deadline == null ? Optional.empty() : Optional.of(Duration.ofNanos(entry.deadline - now)))));
        return new CacheSnapshot<>(capacity, policy, observed, now, items, statistics());
    }

    @Override
    public ResizeResult<K, V> resize(int newCapacity) {
        validCapacity(newCapacity);
        long now = time();
        int old = capacity;
        var expired = purge(now);
        var victims = new ArrayList<RemovedEntry<K, V>>();
        while (entries.size() > newCapacity) victims.add(evict());
        capacity = newCapacity;
        return new ResizeResult<>(old, capacity, expired, victims);
    }

    @Override
    public CacheStatistics statistics() {
        return new CacheStatistics(hits, misses, expirations, evictions, invalidations, clearRemovals);
    }

    @Override
    public CacheDiagnostics diagnostics() {
        return new CacheDiagnostics(capacity, policy, entries.size(), timedEntries, deadlines.size(), 0);
    }

    @Override
    public void resetStatistics() {
        hits = misses = expirations = evictions = invalidations = clearRemovals = 0;
    }

    /**
     * Błędny/cofnięty tick odrzucamy przed purge. lastTick zatwierdza dopiero purge:
     * nieudany put z przepełnieniem nie przesuwa nawet wewnętrznego punktu czasu.
     */
    private long time() {
        long now = ticker.read();
        if (now < 0 || now < lastTick)
            throw new IllegalStateException("Ticker musi być nieujemny i niemalejący");
        return now;
    }

    private List<RemovedEntry<K, V>> purge(long now) {
        lastTick = now;
        var removed = new ArrayList<RemovedEntry<K, V>>();
        while (!deadlines.isEmpty() && deadlines.peek().deadline() <= now) {
            var ticket = deadlines.poll();
            // Kopiec zawiera dokładnie jeden żywy rekord na klucz z TTL.
            if (entries.get(ticket.key()) == ticket.identity()) {
                Entry<V> entry = remove(ticket.key());
                removed.add(new RemovedEntry<>(ticket.key(), entry.value));
                expirations = increment(expirations);
            }
        }
        return removed;
    }

    private Entry<V> remove(K key) {
        // Fizyczne usunięcie obejmuje recency, LFU i termin. Pozostawiony
        // rekord w którymś indeksie wskazywałby klucz nieobecny w entries.
        Entry<V> entry = entries.remove(key);
        if (entry != null) {
            frequencies.remove(key);
            deadlines.removeKey(key);
        }
        if (entry != null && entry.deadline != null)
            timedEntries--;
        return entry;
    }

    private RemovedEntry<K, V> evict() {
        K key = policy == EvictionPolicy.LFU ? frequencies.victim() : entries.firstEntry().getKey();
        Entry<V> entry = remove(key);
        evictions = increment(evictions);
        return new RemovedEntry<>(key, entry.value);
    }

    private static void validCapacity(int capacity) {
        if (capacity < 1)
            throw new IllegalArgumentException("Pojemność musi być dodatnia");
    }

    private static long increment(long value) {
        return value == Long.MAX_VALUE ? value : value + 1;
    }

    private static long saturatedAdd(long value, int addend) {
        return Long.MAX_VALUE - value < addend ? Long.MAX_VALUE : value + addend;
    }
}
